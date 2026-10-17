package com.clacjz.medical.rag.monitor;

import com.clacjz.medical.rag.service.ElasticsearchService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI链路追踪服务
 *
 * 设计依据: HLD Section 3.1 AI链路可观测性设计
 *
 * 功能:
 * - 记录RAG Pipeline各环节的Span (embedding_query, vector_search, rerank, prompt_construction, llm_generate, answer_verification)
 * - 计算检索质量指标 (Recall@K, Precision@K, MRR, NDCG@K)
 * - 计算生成质量指标 (质量评分, 幻觉率, 引用准确率)
 * - 存储Trace数据到Elasticsearch
 */
@Slf4j
@Service
public class AITracingService {

    private static final String METRIC_KEY_RECALL = "recallAtK";
    private static final String METRIC_KEY_PRECISION = "precisionAtK";
    private static final String METRIC_KEY_MRR = "mrr";
    private static final String METRIC_KEY_NDCG = "ndcgAtK";
    private static final String METRIC_KEY_QUALITY_SCORE = "qualityScore";
    private static final String METRIC_KEY_HALLUCINATION_RATE = "hallucinationRate";
    private static final String METRIC_KEY_CITATION_ACCURACY = "citationAccuracy";

    private static final String ES_INDEX_NAME = "ai_trace";

    private final ElasticsearchService elasticsearchService;
    private final AITracingService self;

    // 内存中的Span缓存（用于快速查询）
    private final Map<String, List<AISpan>> traceCache = new ConcurrentHashMap<>();

    public AITracingService(@Lazy AITracingService self, ElasticsearchService elasticsearchService) {
        this.self = self;
        this.elasticsearchService = elasticsearchService;
    }

    /**
     * 开始一个Span
     */
    public AISpan startSpan(String traceId, String operationName, String spanType) {
        return AISpan.builder()
                .traceId(traceId)
                .spanId(UUID.randomUUID().toString())
                .operationName(operationName)
                .spanType(spanType)
                .startTime(Instant.now())
                .build();
    }

    /**
     * 结束一个Span
     */
    public AISpan endSpan(AISpan span) {
        span.setEndTime(Instant.now());
        span.setDurationMs(java.time.Duration.between(span.getStartTime(), span.getEndTime()).toMillis());
        return span;
    }

    /**
     * 记录完整的Trace
     * 使用 LRU 风格的清理：当缓存超限时，删除最老的 20% 条目而非清空全部
     */
    public void recordTrace(String traceId, List<AISpan> spans) {
        // 防止 traceCache 无限增长，超过阈值时清理最老的条目
        synchronized (traceCache) {
            if (traceCache.size() > 500) {
                // 计算要删除的数量（超过500的部分 + 额外20%）
                int toRemove = (traceCache.size() - 400); // 保留400条
                if (toRemove > 0) {
                    List<String> keysToRemove = new ArrayList<>(traceCache.keySet());
                    Collections.sort(keysToRemove); // 按时间排序（假设更早的key字典序更小或时间戳更早）
                    for (int i = 0; i < toRemove && i < keysToRemove.size(); i++) {
                        traceCache.remove(keysToRemove.get(i));
                    }
                    log.debug("[AITracing] Cache eviction: removed {} old traces, remaining={}",
                            toRemove, traceCache.size());
                }
            }
            traceCache.put(traceId, spans);
        }
        log.debug("[AITracing] Recorded trace: traceId={}, spans={}", traceId, spans.size());

        // 异步存储到Elasticsearch
        self.saveToElasticsearchAsync(traceId, spans);
    }

    /**
     * 计算检索质量指标
     *
     * @param relevantDocs 实际相关文档数
     * @param retrievedDocs 检索返回的文档数
     * @param k 取前K个结果
     * @return 质量指标
     */
    public RetrievalMetrics calculateRetrievalMetrics(List<String> relevantDocs, List<String> retrievedDocs, int k) {
        if (k <= 0) k = 5;

        List<String> topKRetrieved = retrievedDocs.stream().limit(k).collect(Collectors.toList());

        // 计算命中数
        Set<String> relevantSet = new HashSet<>(relevantDocs);
        long hitCount = topKRetrieved.stream().filter(relevantSet::contains).count();

        // Recall@K = 命中数 / 相关文档总数
        double recallAtK = relevantDocs.isEmpty() ? 0 : (double) hitCount / relevantDocs.size();

        // Precision@K = 命中数 / K
        double precisionAtK = topKRetrieved.isEmpty() ? 0 : (double) hitCount / k;

        // MRR (Mean Reciprocal Rank) = 第一个命中文档的排名倒数之和 / 查询数
        double mrr = 0;
        for (int i = 0; i < topKRetrieved.size(); i++) {
            if (relevantSet.contains(topKRetrieved.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }

        // NDCG@K (Normalized Discounted Cumulative Gain)
        double ndcgAtK = calculateNDCG(relevantDocs, topKRetrieved, k);

        return RetrievalMetrics.builder()
                .recallAtK(recallAtK)
                .precisionAtK(precisionAtK)
                .mrr(mrr)
                .ndcgAtK(ndcgAtK)
                .hitCount(hitCount)
                .retrievedCount(k)
                .relevantCount(relevantDocs.size())
                .build();
    }

    /**
     * 计算NDCG@K
     */
    private double calculateNDCG(List<String> relevantDocs, List<String> retrievedDocs, int k) {
        if (relevantDocs.isEmpty()) return 0;

        double dcg = 0;
        Set<String> relevantSet = new HashSet<>(relevantDocs);

        for (int i = 0; i < Math.min(k, retrievedDocs.size()); i++) {
            if (relevantSet.contains(retrievedDocs.get(i))) {
                dcg += 1.0 / Math.log(2 + i);
            }
        }

        // 计算IDCG (Ideal DCG)
        double idcg = 0;
        int relevantCount = Math.min(k, relevantDocs.size());
        for (int i = 0; i < relevantCount; i++) {
            idcg += 1.0 / Math.log(2 + i);
        }

        return idcg > 0 ? dcg / idcg : 0;
    }

    /**
     * 计算生成质量指标
     *
     * @param answer 生成的回答
     * @param contexts 使用的上下文
     * @return 生成质量指标
     */
    public GenerationMetrics calculateGenerationMetrics(String answer, List<String> contexts) {
        int promptTokens = estimateTokens(answer);

        // 简单评估：检查回答中是否引用了上下文
        int citedContexts = 0;
        for (String context : contexts) {
            if (answer.contains(context.substring(0, Math.min(50, context.length())))) {
                citedContexts++;
            }
        }
        double citationAccuracy = contexts.isEmpty() ? 0 : (double) citedContexts / contexts.size();

        // 幻觉率：简单估算（实际应该用更复杂的方法）
        // 这里只是检查回答是否在合理长度范围内
        double hallucinationRate = answer.length() < 10 ? 0.1 : 0.02; // 简化估算

        // 质量评分：综合评估
        double qualityScore = calculateQualityScore(answer, contexts, citationAccuracy, hallucinationRate);

        return GenerationMetrics.builder()
                .qualityScore(qualityScore)
                .hallucinationRate(hallucinationRate)
                .citationAccuracy(citationAccuracy)
                .promptTokens(promptTokens)
                .build();
    }

    private double calculateQualityScore(String answer, List<String> contexts, double citationAccuracy, double hallucinationRate) {
        // 综合评分: 0-100分
        double score = 60; // 基础分

        // 引用准确率加分
        score += citationAccuracy * 20;

        // 低幻觉率加分
        score += (1 - hallucinationRate) * 15;

        // 回答长度适中加分
        if (answer.length() > 50 && answer.length() < 2000) {
            score += 5;
        }

        return Math.min(100, score);
    }

    private int estimateTokens(String text) {
        // 简单估算: 中文约2字符/token，英文约4字符/token
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 3;
    }

    /**
     * 异步保存到Elasticsearch
     */
    @Async
    protected void saveToElasticsearchAsync(String traceId, List<AISpan> spans) {
        int maxRetries = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 确保索引存在
                ensureIndexExists();

                // 将每个Span作为单独文档存储，关联到同一个traceId
                for (AISpan span : spans) {
                    Map<String, Object> document = new HashMap<>();
                    document.put("traceId", span.getTraceId());
                    document.put("spanId", span.getSpanId());
                    document.put("parentSpanId", span.getParentSpanId());
                    document.put("operationName", span.getOperationName());
                    document.put("spanType", span.getSpanType());
                    document.put("startTime", span.getStartTime() != null ? span.getStartTime().toEpochMilli() : null);
                    document.put("endTime", span.getEndTime() != null ? span.getEndTime().toEpochMilli() : null);
                    document.put("durationMs", span.getDurationMs());
                    document.put("inputText", span.getInputText());
                    document.put("outputText", span.getOutputText());
                    document.put("metadata", span.getMetadata());
                    document.put("userId", span.getUserId());
                    document.put("sessionId", span.getSessionId());
                    document.put("collectionId", span.getCollectionId());
                    document.put("similarityScore", span.getSimilarityScore());
                    document.put("tokenCount", span.getTokenCount());
                    document.put("chunkCount", span.getChunkCount());

                    String docId = span.getTraceId() + "_" + span.getSpanId();
                    elasticsearchService.indexDocument(ES_INDEX_NAME, docId, document);
                }
                log.info("[AITracing] Saved trace to ES: traceId={}, spans={}", traceId, spans.size());
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("[AITracing] Failed to save trace to ES, attempt {}/{}: traceId={}, error={}",
                        attempt, maxRetries, traceId, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("[AITracing] Failed to save trace to ES after {} retries: traceId={}, spans={}",
                maxRetries, traceId, spans.size(), lastException);
    }

    /**
     * 确保AI Trace索引存在
     */
    private void ensureIndexExists() throws IOException {
        if (!elasticsearchService.indexExists(ES_INDEX_NAME)) {
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("traceId", Map.of("type", "keyword"));
            mappings.put("spanId", Map.of("type", "keyword"));
            mappings.put("parentSpanId", Map.of("type", "keyword"));
            mappings.put("operationName", Map.of("type", "text"));
            mappings.put("spanType", Map.of("type", "keyword"));
            mappings.put("startTime", Map.of("type", "long"));
            mappings.put("endTime", Map.of("type", "long"));
            mappings.put("durationMs", Map.of("type", "long"));
            mappings.put("inputText", Map.of("type", "text"));
            mappings.put("outputText", Map.of("type", "text"));
            mappings.put("userId", Map.of("type", "keyword"));
            mappings.put("sessionId", Map.of("type", "keyword"));
            mappings.put("collectionId", Map.of("type", "keyword"));
            mappings.put("similarityScore", Map.of("type", "float"));
            mappings.put("tokenCount", Map.of("type", "integer"));
            mappings.put("chunkCount", Map.of("type", "integer"));
            mappings.put("metadata", Map.of("type", "object"));

            elasticsearchService.createIndex(ES_INDEX_NAME, mappings);
            log.info("[AITracing] Created ES index: {}", ES_INDEX_NAME);
        }
    }

    /**
     * 获取追踪覆盖率
     */
    public double getTracingCoverage() {
        // 计算AI链路追踪覆盖率
        // 设计要求: 覆盖率 >= 95%
        // 完整trace至少包含3个核心span（vector_search, prompt_construction, llm_generate）
        long totalTraces = traceCache.size();
        long completeTraces = traceCache.values().stream()
                .filter(spans -> spans.size() >= 3)
                .count();
        return totalTraces > 0 ? (double) completeTraces / totalTraces * 100 : 0;
    }

    @Data
    @Builder
    public static class RetrievalMetrics {
        private double recallAtK;
        private double precisionAtK;
        private double mrr;
        private double ndcgAtK;
        private long hitCount;
        private int retrievedCount;
        private int relevantCount;
    }

    @Data
    @Builder
    public static class GenerationMetrics {
        private double qualityScore;
        private double hallucinationRate;
        private double citationAccuracy;
        private int promptTokens;
    }
}