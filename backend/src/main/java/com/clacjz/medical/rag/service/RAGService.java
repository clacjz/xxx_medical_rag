package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.dto.ChatQueryRequest;
import com.clacjz.medical.rag.model.dto.QueryResultDTO;
import com.clacjz.medical.rag.model.dto.SearchResult;
import com.clacjz.medical.rag.model.dto.StreamEventDTO;
import com.clacjz.medical.rag.model.dto.ner.NerResponse;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AISpan;
import com.clacjz.medical.rag.monitor.AITracingService;
import io.micrometer.core.instrument.Counter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final HybridSearchService searchService;
    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    private final RAGProperties ragProperties;
    private final NerClient nerClient;
    private final AITracingService aiTracingService;
    private final AIMetricsService metricsService;
    private final MedicalTermNormalizer medicalTermNormalizer;
    private final CollectionMapper collectionMapper;
    private final DocumentMapper documentMapper;

    enum QueryType {
        METADATA_QUERY,     // 元问题：关于知识库本身
        SEMANTIC_QUERY,     // 语义型：机制/概念/关系，需要语义理解
        EXACT_MATCH_QUERY,  // 精确匹配型：用法/剂量/副作用，需要精确原文匹配
        HYBRID_QUERY,       // 混合型：既有精确术语又有语义需求
        NEGATION_QUERY      // 否定/排除型：包含不能/不宜/禁止等否定词，检索走HYBRID，回答需保留否定语义
    }
    record RewriteResult(List<String> rewrittenQueries, QueryType queryType) {
        /** 向后兼容：取第一个改写 query，空时返回 null 让调用方 fallback */
        public String rewrittenQuery() {
            return rewrittenQueries.isEmpty() ? null : rewrittenQueries.get(0);
        }
    }

    private static final String NO_EXPANSION_PROMPT =
            "你是一个基于文档的问答助手。请严格遵循以下规则：\n" +
            "1. 仅基于以下检索到的上下文内容回答用户问题\n" +
            "2. 每句话末尾必须用 {{数字}} 标注信息来源，数字对应上方【文档 X】的编号\n" +
            "3. 如果上下文中没有足够信息，直接回答「根据现有文档，无法回答该问题」，不要编造\n" +
            "4. 不要添加任何检索内容中没有的信息\n\n" +
            "检索到的上下文：\n%s";

    private static final String NO_EXPANSION_EMPTY_PROMPT =
            "你是一个基于文档的问答助手。请严格遵守以下规则：\n" +
            "1. 系统已在知识库中检索，但未找到与用户问题相关的文档内容\n" +
            "2. 请直接回答：根据现有文档，无法回答该问题\n" +
            "3. 不要编造信息，不要使用一般知识自行回答\n" +
            "4. 可以建议用户更换关键词或选择其他知识库重新提问\n\n" +
            "检索结果：\n%s";

    private static final String DEFAULT_PROMPT =
            "你是一个知识丰富的问答助手。请结合以下上下文来回答用户问题。\n" +
            "如果上下文明确说明未检索到相关内容，请基于您的一般知识回答，\n" +
            "但要明确告知用户这是基于AI知识库而非文档检索。\n\n" +
            "上下文：\n%s";

    private static final int DEFAULT_TOP_K = 5;

    private Counter pipelineRequestsTotal;
    private Counter pipelineSuccessTotal;
    private Counter llmTokensTotal;
    private Counter llmPromptTokensTotal;
    private Counter llmCompletionTokensTotal;

    @PostConstruct
    public void initMetrics() {
        pipelineRequestsTotal = Counter.builder("ai_pipeline_requests_total")
                .description("Total number of AI pipeline requests")
                .register(metricsService.getMeterRegistry());

        pipelineSuccessTotal = Counter.builder("ai_pipeline_requests_success")
                .description("Number of successful AI pipeline requests")
                .register(metricsService.getMeterRegistry());

        llmTokensTotal = Counter.builder("ai_llm_tokens_total")
                .description("Total number of LLM tokens used")
                .tag("type", "total")
                .register(metricsService.getMeterRegistry());

        llmPromptTokensTotal = Counter.builder("ai_llm_tokens_total")
                .description("Total number of LLM prompt tokens")
                .tag("type", "prompt")
                .register(metricsService.getMeterRegistry());

        llmCompletionTokensTotal = Counter.builder("ai_llm_tokens_total")
                .description("Total number of LLM completion tokens")
                .tag("type", "completion")
                .register(metricsService.getMeterRegistry());
    }

    public QueryResultDTO query(ChatQueryRequest request) {
        pipelineRequestsTotal.increment();

        String traceId = UUID.randomUUID().toString();
        /* 使用线程安全列表：executeSingleSearch 被多 query 模式的 CompletableFuture 并行调用，
           多个线程同时 add span 会导致 ArrayList 内部数组越界 */
        List<AISpan> spans = Collections.synchronizedList(new ArrayList<>());
        long totalStartTime = System.currentTimeMillis();

        // 1. Embedding is performed inside searchService, captured as vector_search span
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;

        // 1a. Query Rewrite + 问题分类（合并为一次 LLM 调用）
        String queryToSearch = request.getQuery();
        QueryType queryType = QueryType.HYBRID_QUERY;  // 默认混合型（向后兼容）
        RewriteResult rewriteResult = null;  // 在 if 块外声明，供后续多查询使用
        if (ragProperties.getQueryRewrite().isEnabled()) {
            rewriteResult = doQueryRewrite(request.getQuery(), request.getHistory());
            if (rewriteResult != null) {
                queryType = rewriteResult.queryType();
                String rewritten = rewriteResult.rewrittenQuery();
                if (rewritten != null && !rewritten.equals(request.getQuery())) {
                    queryToSearch = rewritten;
                    log.info("Query rewritten: '{}' -> '{}'", request.getQuery(), queryToSearch);
                }
            }
        } else {
            // query-classification.enabled 可通过独立的配置开关控制
        }

        // 如果分类为元问题，直接返回元回答，跳过向量检索和 LLM 生成
        if (queryType == QueryType.METADATA_QUERY) {
            AISpan metadataSpan = aiTracingService.startSpan(traceId, "metadata_query", "metadata_query");
            metadataSpan.setInputText(request.getQuery());
            try {
                String metadataAnswer = handleMetadataQuery(queryToSearch, request.getCollectionIds());
                metadataSpan.setOutputText(metadataAnswer);
                metadataSpan.setDurationMs(System.currentTimeMillis() - totalStartTime);
                aiTracingService.endSpan(metadataSpan);
                aiTracingService.recordTrace(traceId, List.of(metadataSpan));

                pipelineSuccessTotal.increment();
                QueryResultDTO result = QueryResultDTO.builder()
                        .answer(metadataAnswer)
                        .queryType("metadata")
                        .build();
                log.info("Metadata query answered: query='{}', answerLength={}", request.getQuery(), metadataAnswer.length());
                return result;
            } catch (Exception e) {
                aiTracingService.endSpan(metadataSpan);
                metadataSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                aiTracingService.recordTrace(traceId, List.of(metadataSpan));
                throw e;
            }
        }

        // 1b. 根据分类执行不同检索策略
        try {
            List<SearchResult> searchResults;

            // 确定搜索 query 列表：多 query 改写/分解 → 循环检索；否则单 query
            boolean hasMultipleQueries = rewriteResult != null
                    && rewriteResult.rewrittenQueries().size() > 1;
            List<String> searchQueries;
            if (hasMultipleQueries) {
                searchQueries = rewriteResult.rewrittenQueries();
                log.info("Multi-query search active: {} queries from rewrite/decomposition", searchQueries.size());
            } else {
                searchQueries = List.of(queryToSearch);
            }

            if (searchQueries.size() > 1) {
                // 多 query 模式：并行检索各变体后合并
                QueryType finalQueryType = queryType; // 用于 lambda（保持 effectively final）
                List<CompletableFuture<List<SearchResult>>> futures = searchQueries.stream()
                        .map(sq -> CompletableFuture.supplyAsync(() ->
                                executeSingleSearch(sq, finalQueryType, request, topK, traceId, spans)))
                        .collect(Collectors.toList());
                List<List<SearchResult>> allResults = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());
                boolean allEmpty = allResults.stream().allMatch(r -> r == null || r.isEmpty());
                if (allEmpty) {
                    log.warn("Multi-query search: all queries returned empty results for query='{}'", request.getQuery());
                    searchResults = Collections.emptyList();
                } else {
                    SearchResultMerger.MergeStrategy strategy = SearchResultMerger.MergeStrategy.valueOf(
                            ragProperties.getQueryRewrite().getMultiQueryMergeStrategy());
                    searchResults = SearchResultMerger.merge(allResults, topK, strategy);
                    log.info("Multi-query merged: {} queries -> {} unique results", searchQueries.size(), searchResults.size());
                }

            } else {
                // 单 query 模式（原行为）
                String sq = searchQueries.get(0);

                // 精确匹配型：跳过术语归一化和 NER，直接 ES match_phrase 检索
                if (queryType == QueryType.EXACT_MATCH_QUERY) {
                    long searchStart = System.currentTimeMillis();
                    AISpan searchSpan = aiTracingService.startSpan(traceId, "exact_match_search", AISpan.TYPE_VECTOR_SEARCH);
                    searchSpan.setInputText(sq);
                    spans.add(searchSpan);

                    searchResults = searchService.searchExactMatch(
                            sq,
                            request.getCollectionIds(),
                            topK,
                            request.getSimilarityThreshold(),
                            request.getUserId()
                    );

                    if (searchResults == null) searchResults = Collections.emptyList();
                    searchSpan.setChunkCount(searchResults.size());
                    searchSpan = aiTracingService.endSpan(searchSpan);
                    metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());

                } else {
                    // 语义型 / 混合型：先术语归一化
                    String normalizedQuery = medicalTermNormalizer.normalize(sq);
                    queryToSearch = normalizedQuery;

                    // Query-side NER: 构建 Qdrant payload filter
                    Map<String, Object> queryFilter = null;
                    if (ragProperties.getNer().isEnabled()) {
                        queryFilter = buildQueryFilter(normalizedQuery);
                        if (queryFilter != null && !queryFilter.isEmpty()) {
                            log.info("Query NER filter built: {} for query='{}'", queryFilter, normalizedQuery);
                        }
                    }

                    long searchStart = System.currentTimeMillis();
                    if (queryType == QueryType.SEMANTIC_QUERY) {
                        // 语义型：纯向量检索，保持原始向量分数
                        AISpan searchSpan = aiTracingService.startSpan(traceId, "semantic_search", AISpan.TYPE_VECTOR_SEARCH);
                        searchSpan.setInputText(normalizedQuery);
                        spans.add(searchSpan);

                        if (queryFilter != null && !queryFilter.isEmpty()) {
                            searchResults = searchService.searchVectorOnly(
                                    normalizedQuery,
                                    request.getCollectionIds(),
                                    topK,
                                    request.getSimilarityThreshold(),
                                    request.getUserId(),
                                    queryFilter
                            );
                        } else {
                            searchResults = searchService.searchVectorOnly(
                                    normalizedQuery,
                                    request.getCollectionIds(),
                                    topK,
                                    request.getSimilarityThreshold(),
                                    request.getUserId()
                            );
                        }

                        if (searchResults == null) searchResults = Collections.emptyList();
                        searchSpan.setChunkCount(searchResults.size());
                        searchSpan = aiTracingService.endSpan(searchSpan);
                        metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());

                    } else {
                        // HYBRID_QUERY：当前混合检索行为（Qdrant + ES + RRF）
                        AISpan searchSpan = aiTracingService.startSpan(traceId, "vector_search", AISpan.TYPE_VECTOR_SEARCH);
                        searchSpan.setInputText(normalizedQuery);
                        spans.add(searchSpan);

                        if (queryFilter != null && !queryFilter.isEmpty()) {
                            searchResults = searchService.search(
                                    normalizedQuery,
                                    request.getCollectionIds(),
                                    topK,
                                    request.getSimilarityThreshold(),
                                    request.getUserId(),
                                    queryFilter
                            );
                        } else {
                            searchResults = searchService.search(
                                    normalizedQuery,
                                    request.getCollectionIds(),
                                    topK,
                                    request.getSimilarityThreshold(),
                                    request.getUserId()
                            );
                        }

                        if (searchResults == null) searchResults = Collections.emptyList();
                        searchSpan.setChunkCount(searchResults.size());
                        searchSpan = aiTracingService.endSpan(searchSpan);
                        metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());
                    }
                }
            }

            // 3. Prompt Construction Span
            AISpan promptSpan = aiTracingService.startSpan(traceId, "prompt_construction", AISpan.TYPE_PROMPT_CONSTRUCTION);
            spans.add(promptSpan);
            String context = buildContext(searchResults);
            Boolean noExpansion = request.getNoExpansion();
            boolean hasResults = !searchResults.isEmpty();
            String systemPrompt = buildSystemPrompt(noExpansion != null && noExpansion, hasResults, context, queryType);
            promptSpan = aiTracingService.endSpan(promptSpan);
            promptSpan.setInputText(request.getQuery());
            promptSpan.setOutputText(systemPrompt);

            // 4. LLM Generate Span
            AISpan llmSpan = aiTracingService.startSpan(traceId, "llm_generate", AISpan.TYPE_LLM_GENERATE);
            spans.add(llmSpan);
            long llmStart = System.currentTimeMillis();

            LLMService.GenerateResult llmResult;
            if (request.getThinkingMaxTokens() != null) {
                llmResult = llmService.generateWithReasoning(systemPrompt, request.getQuery(),
                        request.getTemperature(), request.getMaxTokens(),
                        request.getTopP(),
                        Boolean.TRUE.equals(request.getEnableHistory()), request.getHistory(),
                        Boolean.TRUE.equals(request.getThinkingEnabled()),
                        request.getThinkingMaxTokens());
            } else {
                llmResult = llmService.generateWithReasoning(systemPrompt, request.getQuery(),
                        request.getTemperature(), request.getMaxTokens(),
                        request.getTopP(),
                        Boolean.TRUE.equals(request.getEnableHistory()), request.getHistory(),
                        Boolean.TRUE.equals(request.getThinkingEnabled()));
            }
            String answer = llmResult.getContent();
            String reasoning = llmResult.getReasoning();

            // noExpansion 开启时校验回答是否基于检索内容
            answer = validateAnswerGrounded(answer, searchResults, noExpansion != null && noExpansion);

            long llmDuration = System.currentTimeMillis() - llmStart;
            llmSpan = aiTracingService.endSpan(llmSpan);
            llmSpan.setInputText(request.getQuery());
            llmSpan.setOutputText(answer);

            metricsService.recordLlmGenerateLatency(llmDuration);
            metricsService.recordFirstTokenLatency(llmStart - totalStartTime);

            // 使用 EmbeddingService.tokenize() 统计 token 用量
            int promptTokens = embeddingService.tokenize(systemPrompt) + embeddingService.tokenize(request.getQuery());
            int completionTokens = embeddingService.tokenize(answer);
            llmPromptTokensTotal.increment(promptTokens);
            llmCompletionTokensTotal.increment(completionTokens);
            llmTokensTotal.increment(promptTokens + completionTokens);

            // 5. Answer Verification Span
            AISpan verifySpan = aiTracingService.startSpan(traceId, "answer_verification", AISpan.TYPE_ANSWER_VERIFICATION);
            spans.add(verifySpan);
            // 跳过 RetrievalMetrics 计算：生产环境无 ground-truth 相关文档集，
            // 传入相同列表会导致 recall/precision 恒为 1.0，指标无意义
            log.debug("Skipping retrieval metrics (no ground-truth relevant docs available)");

            AITracingService.GenerationMetrics generationMetrics = aiTracingService.calculateGenerationMetrics(
                    answer,
                    searchResults.stream().map(SearchResult::getText).collect(Collectors.toList())
            );
            metricsService.recordGenerationMetrics(generationMetrics);

            verifySpan = aiTracingService.endSpan(verifySpan);

            long totalTime = System.currentTimeMillis() - totalStartTime;
            metricsService.recordTotalQueryLatency(totalTime);

            // 已移除 embedding span，耗时计入 vector_search span
            aiTracingService.recordTrace(traceId, spans);

            metricsService.recordTracingCoverage(aiTracingService.getTracingCoverage());

            pipelineSuccessTotal.increment();

            List<QueryResultDTO.SourceDTO> sources = searchResults.stream()
                    .map(r -> QueryResultDTO.SourceDTO.builder()
                            .documentId(r.getDocumentId())
                            .title(r.getTitle())
                            .snippet(r.getSnippet())
                            .score(r.getScore())
                            .collectionId(r.getCollectionId())
                            .startPage(r.getStartPage())
                            .endPage(r.getEndPage())
                            .build())
                    .collect(Collectors.toList());

            String queryTypeStr = switch (queryType) {
                case SEMANTIC_QUERY -> "semantic";
                case EXACT_MATCH_QUERY -> "exact_match";
                case NEGATION_QUERY -> "negation";
                case METADATA_QUERY -> "metadata";
                default -> "hybrid";
            };

            return QueryResultDTO.builder()
                    .answer(answer)
                    .reasoning(reasoning)
                    .sources(sources)
                    .usedExpansion(!Boolean.TRUE.equals(request.getNoExpansion()))
                    .tokensUsed(completionTokens)
                    .promptTokens(promptTokens)
                    .generationTimeMs(totalTime)
                    .queryType(queryTypeStr)
                    .build();
        } catch (Exception e) {
            // 结束所有已开始但未完成的 span
            for (AISpan s : spans) {
                if (s.getEndTime() == null) {
                    aiTracingService.endSpan(s);
                }
            }
            // 在第一个 span 上标记错误信息
            if (!spans.isEmpty()) {
                spans.get(0).setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
            }
            aiTracingService.recordTrace(traceId, spans);
            log.error("[AITracing] RAG query failed, recorded partial spans: traceId={}, spans={}, error={}",
                    traceId, spans.size(), e.getMessage());
            throw e;
        }
    }

    public Flux<StreamEventDTO> streamQuery(ChatQueryRequest request) {
        return streamQuery(request, UUID.randomUUID().toString());
    }

    public Flux<StreamEventDTO> streamQuery(ChatQueryRequest request, String traceId) {
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;
        log.info("RAG streamQuery started: query={}, collectionIds={}, topK={}",
                request.getQuery(), request.getCollectionIds(), topK);

        // 0. Query Rewrite + 问题分类（合并为一次 LLM 调用）
        String originalQuery = request.getQuery();
        String queryToSearch = originalQuery;
        QueryType queryType = QueryType.HYBRID_QUERY;  // 默认混合型（向后兼容）
        Flux<StreamEventDTO> rewriteEvent = Flux.empty();
        RewriteResult rewriteResult = null;  // 在 if 块外声明，供后续多查询使用

        pipelineRequestsTotal.increment();

        if (ragProperties.getQueryRewrite().isEnabled()) {
            long rewriteStart = System.currentTimeMillis();
            rewriteResult = doQueryRewrite(originalQuery, request.getHistory());
            long rewriteTime = System.currentTimeMillis() - rewriteStart;

            if (rewriteResult != null) {
                queryType = rewriteResult.queryType();
                String rewrittenQuery = rewriteResult.rewrittenQuery();
                List<String> allQueries = rewriteResult.rewrittenQueries();
                boolean hasMultiQuery = allQueries.size() > 1;

                if (rewrittenQuery != null && !rewrittenQuery.equals(originalQuery)) {
                    queryToSearch = rewrittenQuery;
                    log.info("Query rewritten: '{}' -> '{}' ({}ms, {} queries)",
                            originalQuery, queryToSearch, rewriteTime, allQueries.size());

                    StreamEventDTO.StreamEventDTOBuilder eventBuilder = StreamEventDTO.builder()
                            .type("rewrite")
                            .rewrittenQuery(rewrittenQuery);
                    if (hasMultiQuery) {
                        eventBuilder.rewrittenQueries(allQueries);
                        eventBuilder.queryCount(allQueries.size());
                    }
                    rewriteEvent = Flux.just(eventBuilder.build());
                }
            }
        } else {
            // query-classification.enabled 可通过独立的配置开关控制
        }

        // 如果分类为元问题，直接返回元回答的流式响应
        if (queryType == QueryType.METADATA_QUERY) {
            AISpan metadataSpan = aiTracingService.startSpan(traceId, "metadata_query", "metadata_query");
            metadataSpan.setInputText(originalQuery);
            try {
                String metadataAnswer = handleMetadataQuery(queryToSearch, request.getCollectionIds());

                metadataSpan.setOutputText(metadataAnswer);
                aiTracingService.endSpan(metadataSpan);
                aiTracingService.recordTrace(traceId, List.of(metadataSpan));

                pipelineSuccessTotal.increment();
                log.info("Metadata stream query answered: query='{}', answerLength={}", originalQuery, metadataAnswer.length());

                Flux<StreamEventDTO> metadataEvent = Flux.just(StreamEventDTO.builder()
                        .type("metadata")
                        .queryType("metadata")
                        .build());

                Flux<StreamEventDTO> tokenEvents = simulateStreaming(metadataAnswer);
                return metadataEvent.concatWith(tokenEvents);
            } catch (Exception e) {
                aiTracingService.endSpan(metadataSpan);
                metadataSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                aiTracingService.recordTrace(traceId, List.of(metadataSpan));
                return Flux.error(e);
            }
        }

        // 非元问题时，发出分类事件便于前端展示检索策略
        Flux<StreamEventDTO> classificationEvent = Flux.empty();
        if (queryType != QueryType.METADATA_QUERY) {
            String queryTypeStr = switch (queryType) {
                case SEMANTIC_QUERY -> "semantic";
                case EXACT_MATCH_QUERY -> "exact_match";
                case NEGATION_QUERY -> "negation";
                default -> "hybrid";
            };
            classificationEvent = Flux.just(StreamEventDTO.builder()
                    .type("classification")
                    .queryType(queryTypeStr)
                    .build());
        }

        // 1. 根据分类执行不同检索策略
        List<SearchResult> searchResults;

        boolean hasMultipleQueries = rewriteResult != null
                && rewriteResult.rewrittenQueries().size() > 1;

        if (hasMultipleQueries) {
            // 多 query 模式：并行检索各变体后合并
            List<String> searchQueries = rewriteResult.rewrittenQueries();
            log.info("Multi-query stream expansion active: {} queries", searchQueries.size());

            // 使用线程安全列表收集 span（CompletableFuture 可能并行修改）
            List<AISpan> multiSpans = Collections.synchronizedList(new ArrayList<>());
            QueryType finalQueryType = queryType; // 用于 lambda（保持 effectively final）
            List<CompletableFuture<List<SearchResult>>> futures = searchQueries.stream()
                    .map(sq -> CompletableFuture.supplyAsync(() ->
                            executeSingleSearch(sq, finalQueryType, request, topK, traceId, multiSpans)))
                    .collect(Collectors.toList());
            List<List<SearchResult>> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            if (!multiSpans.isEmpty()) {
                aiTracingService.recordTrace(traceId, multiSpans);
            }

            boolean allEmpty = allResults.stream().allMatch(r -> r == null || r.isEmpty());
            if (allEmpty) {
                log.warn("Multi-query stream: all queries returned empty results for query='{}'", originalQuery);
                searchResults = Collections.emptyList();
            } else {
                SearchResultMerger.MergeStrategy strategy = SearchResultMerger.MergeStrategy.valueOf(
                        ragProperties.getQueryRewrite().getMultiQueryMergeStrategy());
                searchResults = SearchResultMerger.merge(allResults, topK, strategy);
                log.info("Multi-query stream merged: {} queries -> {} unique results", searchQueries.size(), searchResults.size());
            }

        } else if (queryType == QueryType.EXACT_MATCH_QUERY) {
            // 精确匹配型：跳过归一化和 NER，直接 ES match_phrase
            AISpan searchSpan = aiTracingService.startSpan(traceId, "exact_match_search", AISpan.TYPE_VECTOR_SEARCH);
            searchSpan.setInputText(queryToSearch);
            searchSpan.setUserId(request.getUserId());
            searchSpan.setSessionId(request.getSessionId());

            try {
                searchResults = searchService.searchExactMatch(
                        queryToSearch,
                        request.getCollectionIds(),
                        topK,
                        request.getSimilarityThreshold(),
                        request.getUserId()
                );
            } catch (Exception e) {
                aiTracingService.endSpan(searchSpan);
                searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                aiTracingService.recordTrace(traceId, List.of(searchSpan));
                log.error("RAG streamQuery exact_match search failed: traceId={}, error={}", traceId, e.getMessage());
                return Flux.error(e);
            }
            if (searchResults == null) searchResults = Collections.emptyList();
            searchSpan.setChunkCount(searchResults.size());
            aiTracingService.endSpan(searchSpan);
            aiTracingService.recordTrace(traceId, List.of(searchSpan));
            final int exactMatchCount = searchResults.size();
            log.info("RAG exact_match search completed: found {} results", exactMatchCount);

        } else {
            // 语义型 / 混合型：先术语归一化 + NER
            queryToSearch = medicalTermNormalizer.normalize(queryToSearch);

            Map<String, Object> queryFilter = null;
            if (ragProperties.getNer().isEnabled()) {
                queryFilter = buildQueryFilter(queryToSearch);
            }

            if (queryType == QueryType.SEMANTIC_QUERY) {
                // 语义型：纯向量检索
                AISpan searchSpan = aiTracingService.startSpan(traceId, "semantic_search", AISpan.TYPE_VECTOR_SEARCH);
                searchSpan.setInputText(queryToSearch);
                searchSpan.setUserId(request.getUserId());
                searchSpan.setSessionId(request.getSessionId());

                try {
                    if (queryFilter != null && !queryFilter.isEmpty()) {
                        searchResults = searchService.searchVectorOnly(
                                queryToSearch,
                                request.getCollectionIds(),
                                topK,
                                request.getSimilarityThreshold(),
                                request.getUserId(),
                                queryFilter
                        );
                    } else {
                        searchResults = searchService.searchVectorOnly(
                                queryToSearch,
                                request.getCollectionIds(),
                                topK,
                                request.getSimilarityThreshold(),
                                request.getUserId()
                        );
                    }
                } catch (Exception e) {
                    aiTracingService.endSpan(searchSpan);
                    searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                    aiTracingService.recordTrace(traceId, List.of(searchSpan));
                    log.error("RAG streamQuery semantic search failed: traceId={}, error={}", traceId, e.getMessage());
                    return Flux.error(e);
                }
                if (searchResults == null) searchResults = Collections.emptyList();
                searchSpan.setChunkCount(searchResults.size());
                aiTracingService.endSpan(searchSpan);
                aiTracingService.recordTrace(traceId, List.of(searchSpan));
                final int semanticCount = searchResults.size();
                log.info("RAG semantic search completed: found {} results", semanticCount);

            } else {
                // HYBRID_QUERY：当前混合检索行为
                AISpan searchSpan = aiTracingService.startSpan(traceId, "vector_search", AISpan.TYPE_VECTOR_SEARCH);
                searchSpan.setInputText(queryToSearch);
                searchSpan.setUserId(request.getUserId());
                searchSpan.setSessionId(request.getSessionId());

                try {
                    if (queryFilter != null && !queryFilter.isEmpty()) {
                        searchResults = searchService.search(
                                queryToSearch,
                                request.getCollectionIds(),
                                topK,
                                request.getSimilarityThreshold(),
                                request.getUserId(),
                                queryFilter
                        );
                    } else {
                        searchResults = searchService.search(
                                queryToSearch,
                                request.getCollectionIds(),
                                topK,
                                request.getSimilarityThreshold(),
                                request.getUserId()
                        );
                    }
                } catch (Exception e) {
                    aiTracingService.endSpan(searchSpan);
                    searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                    aiTracingService.recordTrace(traceId, List.of(searchSpan));
                    log.error("RAG streamQuery hybrid search failed: traceId={}, error={}", traceId, e.getMessage());
                    return Flux.error(e);
                }
                if (searchResults == null) searchResults = Collections.emptyList();
                searchSpan.setChunkCount(searchResults.size());
                aiTracingService.endSpan(searchSpan);
                aiTracingService.recordTrace(traceId, List.of(searchSpan));
                final int hybridCount = searchResults.size();
                log.info("RAG hybrid search completed: found {} results", hybridCount);
            }
        }
        final int resultCount = searchResults == null ? 0 : searchResults.size();

        // 2. Emit source events for each search result
        Flux<StreamEventDTO> sourceEvents = Flux.fromIterable(
                searchResults.stream()
                        .map(r -> StreamEventDTO.builder()
                                .type("source")
                                .documentId(r.getDocumentId())
                                .title(r.getTitle())
                                .snippet(r.getSnippet())
                                .score(r.getScore())
                                .collectionId(r.getCollectionId())
                                .startPage(r.getStartPage())
                                .endPage(r.getEndPage())
                                .build())
                        .collect(Collectors.toList())
        );

        // 3. Prompt Construction Span
        AISpan promptSpan = aiTracingService.startSpan(traceId, "prompt_construction", AISpan.TYPE_PROMPT_CONSTRUCTION);
        promptSpan.setInputText(request.getQuery());
        String context = buildContext(searchResults);
        Boolean noExpansion = request.getNoExpansion();
        boolean hasResults = !searchResults.isEmpty();
        String systemPrompt = buildSystemPrompt(noExpansion != null && noExpansion, hasResults, context, queryType);
        promptSpan.setOutputText(systemPrompt);
        aiTracingService.endSpan(promptSpan);
        aiTracingService.recordTrace(traceId, List.of(promptSpan));
        log.info("RAG prompt built: context length={}, noExpansion={}", context.length(), noExpansion);

        // 4. LLM Generate Span (reactive, ended in doOnComplete/doOnError)
        StringBuilder allTokens = new StringBuilder();
        boolean doGroundingCheck = Boolean.TRUE.equals(request.getNoExpansion()) && hasResults;

        AISpan llmSpan = aiTracingService.startSpan(traceId, "llm_generate", AISpan.TYPE_LLM_GENERATE);
        llmSpan.setInputText(request.getQuery());

        Flux<StreamEventDTO> tokenEvents;
        try {
            Flux<StreamEventDTO> streamFlux;
            if (request.getThinkingMaxTokens() != null) {
                streamFlux = llmService.streamGenerate(systemPrompt, request.getQuery(),
                        request.getTemperature(), request.getMaxTokens(),
                        request.getTopP(),
                        Boolean.TRUE.equals(request.getEnableHistory()), request.getHistory(),
                        Boolean.TRUE.equals(request.getThinkingEnabled()),
                        request.getThinkingMaxTokens());
            } else {
                streamFlux = llmService.streamGenerate(systemPrompt, request.getQuery(),
                        request.getTemperature(), request.getMaxTokens(),
                        request.getTopP(),
                        Boolean.TRUE.equals(request.getEnableHistory()), request.getHistory(),
                        Boolean.TRUE.equals(request.getThinkingEnabled()));
            }
            tokenEvents = streamFlux
                    .doOnNext(event -> {
                        if ("token".equals(event.getType()) && event.getContent() != null) {
                            allTokens.append(event.getContent());
                        }
                    })
                    .doOnComplete(() -> {
                        llmSpan.setOutputText(allTokens.toString());
                        aiTracingService.endSpan(llmSpan);
                        aiTracingService.recordTrace(traceId, List.of(llmSpan));
                        pipelineSuccessTotal.increment();
                    })
                    .doOnError(e -> {
                        log.error("RAG streamQuery error", e);
                        llmSpan.setMetadata(Map.of("error", e.getMessage()));
                        aiTracingService.endSpan(llmSpan);
                        aiTracingService.recordTrace(traceId, List.of(llmSpan));
                    });
        } catch (Exception e) {
            log.error("RAG streamGenerate failed synchronously", e);
            llmSpan.setMetadata(Map.of("error", e.getMessage()));
            aiTracingService.endSpan(llmSpan);
            aiTracingService.recordTrace(traceId, List.of(llmSpan));
            tokenEvents = Flux.error(e);
        }

        // 5. Answer grounding validation event（noExpansion 模式，在 token 流完成后追加）
        Flux<StreamEventDTO> validationEvent = Flux.defer(() -> {
            if (!doGroundingCheck) {
                return Flux.empty();
            }
            String fullAnswer = allTokens.toString();
            if (fullAnswer.isBlank()) {
                return Flux.empty();
            }
            Pattern citationPattern = Pattern.compile("\\{\\{(\\d+)\\}\\}");
            if (!citationPattern.matcher(fullAnswer).find()) {
                log.warn("No expansion mode: no citations found in stream answer. results={}", resultCount);
                return Flux.just(StreamEventDTO.builder()
                        .type("warning")
                        .content("以上回答未标注信息来源，请参考检索文档核实具体信息。")
                        .build());
            }
            return Flux.empty();
        });

        // 6. Emit: rewrite -> classification -> sources -> tokens -> validation
        Flux<StreamEventDTO> result = rewriteEvent
                .concatWith(classificationEvent)
                .concatWith(sourceEvents)
                .concatWith(tokenEvents)
                .concatWith(validationEvent)
                .doOnError(e -> log.error("RAG concatWith error", e));
        return result;
    }

    private String buildContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "【检索结果】\n知识库中未检索到与问题相关的内容。\n";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("【文档 %d】\n", i + 1));
            if (result.getTitle() != null && !result.getTitle().isEmpty()) {
                sb.append(String.format("标题：%s\n", result.getTitle()));
            }
            // 添加页码位置信息（如果有）
            if (result.getStartPage() != null && result.getStartPage() > 0) {
                if (result.getEndPage() != null && result.getEndPage() > 0
                        && !result.getStartPage().equals(result.getEndPage())) {
                    sb.append(String.format("位置：第 %d-%d 页\n", result.getStartPage(), result.getEndPage()));
                } else {
                    sb.append(String.format("位置：第 %d 页\n", result.getStartPage()));
                }
            }
            sb.append(String.format("内容：%s\n\n", result.getText()));
        }

        return sb.toString();
    }

    private String buildSystemPrompt(boolean noExpansion, boolean hasResults, String context, QueryType queryType) {
        String negationNote = "";
        if (queryType == QueryType.NEGATION_QUERY) {
            negationNote = "\n\n注意：用户问题包含否定/排除语义（如「不能」「不宜」「禁止」「避免」等），不允许回答或推荐用户明确排除或禁止的内容。回答时请明确指出相关限制。";
        }
        if (noExpansion) {
            if (hasResults) {
                return String.format(NO_EXPANSION_PROMPT, context) + negationNote;
            } else {
                return String.format(NO_EXPANSION_EMPTY_PROMPT, context) + negationNote;
            }
        } else {
            String prompt = String.format(DEFAULT_PROMPT, context);
            if (queryType == QueryType.NEGATION_QUERY) {
                prompt += negationNote;
            }
            return prompt;
        }
    }

    /**
     * 验证答案是否基于检索内容（仅 noExpansion=true 时）
     * 检查 {{N}} 引用编号是否都在有效范围内，无引用时追加告警
     */
    private String validateAnswerGrounded(String answer, List<SearchResult> results, boolean noExpansion) {
        if (!noExpansion || results == null || results.isEmpty()) {
            return answer;
        }

        Pattern pattern = Pattern.compile("\\{\\{(\\d+)\\}\\}");
        Matcher matcher = pattern.matcher(answer);
        Set<Integer> citedIds = new HashSet<>();
        while (matcher.find()) {
            citedIds.add(Integer.parseInt(matcher.group(1)));
        }

        for (int id : citedIds) {
            if (id < 1 || id > results.size()) {
                log.warn("Answer cites non-existent document #{} (total {} docs): {}", id, results.size(), answer.substring(0, Math.min(100, answer.length())));
            }
        }

        if (citedIds.isEmpty()) {
            log.warn("No expansion mode: no citations found in answer. results={}", results.size());
            answer += "\n\n> 以上回答由 AI 模型生成，请参考检索文档核实具体信息。";
        }

        return answer;
    }

    /**
     * 从用户查询中提取NER实体并构建Qdrant payload filter
     */
    private Map<String, Object> buildQueryFilter(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        // 医学术语不受长度限制，只要不是空白就进行NER
        if (query.length() < 3) {
            return null;
        }

        NerResponse nerResponse = nerClient.extractEntities(query);
        if (nerResponse == null || nerResponse.getEntities() == null || nerResponse.getEntities().isEmpty()) {
            return null;
        }

        List<String> drugNames = new ArrayList<>();
        List<String> manufacturers = new ArrayList<>();
        List<String> regions = new ArrayList<>();

        for (var entity : nerResponse.getEntities()) {
            if (entity.getText() == null || entity.getText().isBlank()) continue;
            switch (entity.getType()) {
                case "DRUG" -> drugNames.add(entity.getText());
                case "ORG" -> manufacturers.add(entity.getText());
                case "REGION" -> regions.add(entity.getText());
                default -> { }
            }
        }

        Map<String, Object> filter = new HashMap<>();
        if (!drugNames.isEmpty()) {
            filter.put("drug_names", drugNames.stream().distinct().limit(6).toList());
        }
        if (!manufacturers.isEmpty()) {
            filter.put("manufacturers", manufacturers.stream().distinct().limit(6).toList());
        }
        if (!regions.isEmpty()) {
            filter.put("regions", regions.stream().distinct().limit(6).toList());
        }

        if (!filter.isEmpty()) {
            log.debug("Query NER filter built: {} from query='{}'", filter, query);
        }

        return filter.isEmpty() ? null : filter;
    }

    /**
     * 执行单个 query 的检索，包含 span 记录。
     * 供多 query 扩展模式下循环调用。
     */
    private List<SearchResult> executeSingleSearch(
            String query, QueryType queryType, ChatQueryRequest request,
            int topK, String traceId, List<AISpan> spans) {

        if (queryType == QueryType.EXACT_MATCH_QUERY) {
            AISpan searchSpan = aiTracingService.startSpan(traceId, "exact_match_search", AISpan.TYPE_VECTOR_SEARCH);
            searchSpan.setInputText(query);
            spans.add(searchSpan);

            try {
                List<SearchResult> results = searchService.searchExactMatch(
                        query, request.getCollectionIds(), topK,
                        request.getSimilarityThreshold(), request.getUserId());
                if (results == null) results = Collections.emptyList();
                searchSpan.setChunkCount(results.size());
                aiTracingService.endSpan(searchSpan);
                metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());
                return results;
            } catch (Exception e) {
                aiTracingService.endSpan(searchSpan);
                searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                log.warn("Multi-query exact_match search failed for query='{}': {}", query, e);
                return Collections.emptyList();
            }
        }

        // SEMANTIC / HYBRID: 先归一化，再 NER
        String normalized = medicalTermNormalizer.normalize(query);

        Map<String, Object> queryFilter = null;
        if (ragProperties.getNer().isEnabled()) {
            queryFilter = buildQueryFilter(normalized);
        }

        if (queryType == QueryType.SEMANTIC_QUERY) {
            AISpan searchSpan = aiTracingService.startSpan(traceId, "semantic_search", AISpan.TYPE_VECTOR_SEARCH);
            searchSpan.setInputText(normalized);
            spans.add(searchSpan);

            try {
                List<SearchResult> results;
                if (queryFilter != null && !queryFilter.isEmpty()) {
                    results = searchService.searchVectorOnly(
                            normalized, request.getCollectionIds(), topK,
                            request.getSimilarityThreshold(), request.getUserId(), queryFilter);
                } else {
                    results = searchService.searchVectorOnly(
                            normalized, request.getCollectionIds(), topK,
                            request.getSimilarityThreshold(), request.getUserId());
                }
                if (results == null) results = Collections.emptyList();
                searchSpan.setChunkCount(results.size());
                aiTracingService.endSpan(searchSpan);
                metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());
                return results;
            } catch (Exception e) {
                aiTracingService.endSpan(searchSpan);
                searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
                log.warn("Multi-query semantic search failed for query='{}': {}", normalized, e);
                return Collections.emptyList();
            }
        }

        // HYBRID_QUERY
        AISpan searchSpan = aiTracingService.startSpan(traceId, "vector_search", AISpan.TYPE_VECTOR_SEARCH);
        searchSpan.setInputText(normalized);
        spans.add(searchSpan);

        try {
            List<SearchResult> results;
            if (queryFilter != null && !queryFilter.isEmpty()) {
                results = searchService.search(
                        normalized, request.getCollectionIds(), topK,
                        request.getSimilarityThreshold(), request.getUserId(), queryFilter);
            } else {
                results = searchService.search(
                        normalized, request.getCollectionIds(), topK,
                        request.getSimilarityThreshold(), request.getUserId());
            }
            if (results == null) results = Collections.emptyList();
            searchSpan.setChunkCount(results.size());
            aiTracingService.endSpan(searchSpan);
            metricsService.recordVectorSearchLatency(searchSpan.getDurationMs());
            return results;
        } catch (Exception e) {
            aiTracingService.endSpan(searchSpan);
            searchSpan.setMetadata(Map.of("partialTrace", true, "error", e.getMessage()));
            log.warn("Multi-query hybrid search failed for query='{}': {}", normalized, e);
            return Collections.emptyList();
        }
    }

    /**
     * 检测用户问题是否为复合问题（包含对比、并列、多重意图等结构）
     */
    private boolean isLikelyCompoundQuery(String query) {
        if (query == null || query.isBlank()) return false;
        return COMPOUND_PATTERN.matcher(query).find();
    }

    private static final Pattern COMPOUND_PATTERN = Pattern.compile(
            "(?:区别|对比|分别|各自|两者|二者|异同|差异)"
    );

    private RewriteResult doQueryRewrite(String query) {
        return doQueryRewrite(query, null);
    }

    private RewriteResult doQueryRewrite(String query, List<Map<String, String>> history) {
        if (query == null || query.isBlank()) {
            return new RewriteResult(List.of(query), QueryType.HYBRID_QUERY);
        }

        // 构建历史上下文字符串（最近20轮对话）
        String historyContext = "";
        if (history != null && !history.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, history.size() - 40); // 最多取最近40条消息（20轮）
            for (int i = start; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");
                if (role.equals("user")) {
                    sb.append("用户：").append(content).append("\n");
                } else if (role.equals("assistant")) {
                    sb.append("助手：").append(content).append("\n");
                }
            }
            if (!sb.isEmpty()) {
                historyContext = sb.toString();
            }
        }

        boolean hybridClassify = ragProperties.getQueryRewrite().isHybridClassificationEnabled();
        boolean multiQueryEnabled = ragProperties.getQueryRewrite().isMultiQueryEnabled();
        boolean decomposeEnabled = ragProperties.getQueryRewrite().isSubQueryDecompositionEnabled();

        int multiQueryCount = multiQueryEnabled ? ragProperties.getQueryRewrite().getMultiQueryCount() : 1;
        boolean isCompound = decomposeEnabled && isLikelyCompoundQuery(query);
        boolean combinedMode = isCompound && multiQueryEnabled;

        // 组合模式下，控制每个子查询的扩展数，使总数不超过 maxTotalSearchQueries
        int expansionPerQuery = multiQueryCount;
        if (combinedMode) {
            int maxTotal = ragProperties.getQueryRewrite().getMaxTotalSearchQueries();
            // 假设分解后最多 4 个子查询，调低每个的扩展数
            expansionPerQuery = Math.max(1, Math.min(multiQueryCount, maxTotal / 3));
        }

        int maxTokens = 300;
        String rewriteInstruction;

        String classificationInstruction;
        if (hybridClassify) {
            classificationInstruction =
                    "首先判断问题类型，根据问题特征选择最匹配的类型：\n" +
                    "- 元问题（关于知识库本身）→ METADATA_QUERY，" +
                    "改写部分填写子类型（OVERVIEW/RECENT_UPDATES/UPLOAD_GUIDE/SUPPORTED_FORMATS）\n" +
                    "- 语义型问题（概念理解、机制解释、对比比较、开放式问题，需要语义理解）→ SEMANTIC_QUERY，" +
                    "改写时扩展同义词、补充相关术语\n" +
                    "- 精确匹配型问题（具体剂量/用法/副作用/药品信息，需要精确匹配原文）→ EXACT_MATCH_QUERY，" +
                    "改写时保留原文，不要扩展同义词\n" +
                    "- 否定/排除型问题（包含「不能」「不宜」「禁止」「避免」「禁忌」「除外」「不可」等否定词，需要保留否定语义）→ NEGATION_QUERY，" +
                    "改写时保留否定词，不要用同义词替代否定词\n" +
                    "- 混合型问题（既有精确术语又有语义需求，或无法明确归类）→ HYBRID_QUERY，" +
                    "改写时适度改写，保留核心术语\n\n";
        } else {
            classificationInstruction =
                    "首先判断问题类型：\n" +
                    "- 元问题（关于知识库本身，如包含哪些内容、最近更新、怎么上传、支持什么格式等）→ METADATA_QUERY，" +
                    "改写部分填写子类型（OVERVIEW/RECENT_UPDATES/UPLOAD_GUIDE/SUPPORTED_FORMATS）\n" +
                    "- 否定/排除型问题（包含「不能」「不宜」「禁止」等否定词）→ NEGATION_QUERY，改写时保留否定词\n" +
                    "- 检索问题 → HYBRID_QUERY，改写部分输出改写后的query\n\n";
        }

        if (combinedMode) {
            // 先分解后扩展
            rewriteInstruction = String.format(
                    "改写要求：\n" +
                    "1. 首先判断用户问题是否包含多个独立的子问题（如包含「和」「与」「区别」「对比」「分别」「各自」等连词）\n" +
                    "2. 如果是复合问题，先拆分为独立的子查询，每个子查询再生成%d个不同角度的检索query\n" +
                    "3. 如果不是复合问题，生成%d个不同角度的检索query\n" +
                    "4. 保持各子查询原意，不要添加新信息\n" +
                    "5. 去掉重复或过于相似的query\n" +
                    "6. 每个query独立成一行，格式为：改写1：query1\n改写2：query2\n...\n\n",
                    expansionPerQuery, multiQueryCount);
            maxTokens = 500;
        } else if (multiQueryEnabled) {
            // 多查询扩展
            rewriteInstruction = String.format(
                    "改写要求：\n" +
                    "1. 生成%d个不同侧重点的检索query\n" +
                    "2. query1：精确术语版，保留核心医学术语和原文结构\n" +
                    "3. query2：语义扩展版，补充同义词和相关概念\n" +
                    "4. query3：精简概括版，提取最关键检索信息\n" +
                    "5. 保持原意，不要添加新信息\n" +
                    "6. 每个query占一行，格式为：改写1：query1\n改写2：query2\n...\n\n",
                    multiQueryCount);
            maxTokens = 400;
        } else if (isCompound) {
            // 子查询分解
            rewriteInstruction =
                    "改写要求：\n" +
                    "1. 分析用户问题是否为复合问题（包含多个独立子问题、对比或并列结构）\n" +
                    "2. 如果是复合问题，拆分为2-4个独立的检索query，每个query只聚焦一个子问题\n" +
                    "3. 如果不是复合问题，只生成1个改写后的query\n" +
                    "4. 每个子查询应能独立检索到相关信息\n" +
                    "5. 每个query独立成一行，格式为：改写1：query1\n改写2：query2\n...\n\n";
            maxTokens = 400;
        } else {
            // 标准单 query 改写（原行为）
            rewriteInstruction =
                    "改写要求：\n" +
                    "1. 去除口语化表达，保留核心医学术语\n" +
                    "2. 可以补充相关同义词或扩展表达\n" +
                    "3. 保持原意，不要添加新信息\n" +
                    "4. 输出格式为两行：" +
                    "类型：...（见上方说明）\n改写：改写后的query\n\n";
        }

        String classifyFormat;
        if (hybridClassify) {
            classifyFormat = "类型：SEMANTIC_QUERY（或 EXACT_MATCH_QUERY / HYBRID_QUERY / NEGATION_QUERY / METADATA_QUERY）";
        } else {
            classifyFormat = "类型：HYBRID_QUERY（或 NEGATION_QUERY / METADATA_QUERY）";
        }

        // 根据模式选择正确的输出格式示例
        String outputFormatExample;
        if (combinedMode || multiQueryEnabled || isCompound) {
            outputFormatExample = "改写1：query内容\n改写2：query内容\n（或按需输出更多行）";
        } else {
            outputFormatExample = "改写：改写后的query";
        }

        String rewritePrompt;
        if (!historyContext.isEmpty()) {
            rewritePrompt = String.format(
                    "你是一个专业的医学问答助手。请根据对话历史，将用户的当前问题改写为独立、完整的检索query，并判断问题类型。\n\n" +
                    classificationInstruction +
                    rewriteInstruction +
                    "输出格式：\n" + classifyFormat + "\n" +
                    outputFormatExample + "\n\n" +
                    "对话历史：\n%s\n" +
                    "当前问题：%s",
                    historyContext, query
            );
        } else {
            rewritePrompt = String.format(
                    "你是一个专业的医学问答助手。请将用户的原始问题改写为更加清晰、准确的检索query，并判断问题类型。\n\n" +
                    classificationInstruction +
                    rewriteInstruction +
                    "输出格式：\n" + classifyFormat + "\n" +
                    outputFormatExample + "\n\n" +
                    "原始问题：%s",
                    query
            );
        }

        try {
            String response = llmService.generate(
                    rewritePrompt,
                    null,
                    ragProperties.getQueryRewrite().getTemperature(),
                    maxTokens,
                    null,  // topP
                    false, // 无需history
                    null   // 无history
            );
            if (response != null && !response.isBlank()) {
                return parseRewriteResponse(response, query);
            }
            return new RewriteResult(List.of(query), QueryType.HYBRID_QUERY);
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original query. error={}", e.getMessage(), e);
            return new RewriteResult(List.of(query), QueryType.HYBRID_QUERY);
        }
    }

    private RewriteResult parseRewriteResponse(String response, String originalQuery) {
        String[] lines = response.split("\n");
        QueryType queryType = QueryType.HYBRID_QUERY;
        boolean hasMultiFormat = false;
        // 先扫描是否有 改写N： 格式
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("改写\\d+[：:].*")) {
                hasMultiFormat = true;
                break;
            }
        }

        if (hasMultiFormat) {
            // 多 query 格式：改写1：... 改写2：... 改写3：...
            List<String> rewrittenQueries = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("类型：") || line.startsWith("类型:")) {
                    queryType = parseQueryType(line, queryType);
                } else if (line.matches("改写\\d+[：:].*")) {
                    String value = line.replaceFirst("改写\\d+[：:]", "").trim()
                            .replaceAll("^[\"']+|[\"']+$", "");
                    if (!value.isBlank()) {
                        rewrittenQueries.add(value);
                    }
                }
            }
            if (rewrittenQueries.isEmpty()) {
                rewrittenQueries.add(originalQuery);
            }
            return new RewriteResult(rewrittenQueries, queryType);
        } else {
            // 单 query 格式（兼容旧格式）：改写：...
            String rewrittenQuery = originalQuery;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("类型：") || line.startsWith("类型:")) {
                    queryType = parseQueryType(line, queryType);
                } else if (line.startsWith("改写：") || line.startsWith("改写:")) {
                    String value = line.replaceFirst("改写[：:]", "").trim()
                            .replaceAll("^[\"']+|[\"']+$", "");
                    if (!value.isBlank()) {
                        rewrittenQuery = value;
                    }
                }
            }
            return new RewriteResult(List.of(rewrittenQuery), queryType);
        }
    }

    private QueryType parseQueryType(String line, QueryType currentDefault) {
        if (line.contains("METADATA_QUERY")) {
            return QueryType.METADATA_QUERY;
        } else if (line.contains("SEMANTIC_QUERY")) {
            return QueryType.SEMANTIC_QUERY;
        } else if (line.contains("EXACT_MATCH_QUERY")) {
            return QueryType.EXACT_MATCH_QUERY;
        } else if (line.contains("NEGATION_QUERY")) {
            return QueryType.NEGATION_QUERY;
        } else if (line.contains("HYBRID_QUERY") || line.contains("RETRIEVAL_QUERY")) {
            return QueryType.HYBRID_QUERY;
        }
        return currentDefault;
    }

    private String handleMetadataQuery(String metadataSubtype, List<String> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return "请先选择知识库。";
        }

        if (metadataSubtype == null) {
            return "无法识别的问题类型。";
        }

        try {
            List<Collection> collections = collectionMapper.selectActiveByIds(collectionIds);
            if (collections == null || collections.isEmpty()) {
                return "未找到所选知识库的信息。";
            }

            if (metadataSubtype.contains("OVERVIEW")) {
                return buildCollectionOverview(collections);
            } else if (metadataSubtype.contains("RECENT_UPDATES")) {
                return buildRecentUpdates(collections);
            } else if (metadataSubtype.contains("UPLOAD_GUIDE")) {
                return buildUploadGuide();
            } else if (metadataSubtype.contains("SUPPORTED_FORMATS")) {
                return buildSupportedFormats();
            }

            return "无法识别的问题类型。";
        } catch (Exception e) {
            log.error("Failed to query metadata: {}", e.getMessage());
            return "查询知识库信息时发生错误，请稍后重试。";
        }
    }

    private String buildCollectionOverview(List<Collection> collections) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前共检索 **").append(collections.size()).append("** 个知识库，包含以下内容：\n\n");

        for (Collection c : collections) {
            sb.append("### ").append(c.getName()).append("\n");
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(c.getDescription()).append("\n\n");
            }
            sb.append("- 文档数量：").append(c.getDocumentCount() != null ? c.getDocumentCount() : 0).append(" 份\n");
            sb.append("- 分块数量：").append(c.getChunkCount() != null ? c.getChunkCount() : 0).append(" 个\n");
            if (c.getTotalSize() != null && c.getTotalSize() > 0) {
                sb.append("- 总大小：").append(formatSize(c.getTotalSize())).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildRecentUpdates(List<Collection> collections) {
        List<String> ids = collections.stream().map(Collection::getId).collect(Collectors.toList());
        List<Document> recentDocs = documentMapper.selectRecentByCollectionIds(ids, 10);

        if (recentDocs == null || recentDocs.isEmpty()) {
            return "暂无最近更新的文档。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**最近更新的文档**（截至 ").append(LocalDate.now()).append("）：\n\n");

        int i = 1;
        for (Document doc : recentDocs) {
            String title = doc.getTitle() != null ? doc.getTitle() : doc.getFileName();
            sb.append(i++).append(". **").append(title).append("**");
            if (doc.getUpdatedAt() != null) {
                sb.append(" — 更新于 ").append(doc.getUpdatedAt().toLocalDate());
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildUploadGuide() {
        return "上传文档到知识库的操作指南：\n\n"
                + "1. 进入**知识库管理**页面\n"
                + "2. 选择目标知识库\n"
                + "3. 点击**上传文档**按钮\n"
                + "4. 选择文件并确认上传\n"
                + "5. 等待系统自动解析、分块和向量化处理\n\n"
                + "支持拖拽上传和批量上传，单文件大小限制请参考系统配置。";
    }

    private String buildSupportedFormats() {
        String[] types = ragProperties.getDocument().getSupportedTypes();
        if (types == null || types.length == 0) {
            return "当前知识库暂未配置支持的文档格式。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前知识库支持以下文档格式：\n\n");
        for (String type : types) {
            if (type == null) continue;
            type = type.trim();
            if (type.isEmpty()) continue;
            String label = switch (type.toLowerCase()) {
                case "pdf" -> "PDF 文档（.pdf）";
                case "docx" -> "Word 文档（.docx）";
                case "md" -> "Markdown 文件（.md）";
                case "txt" -> "纯文本文件（.txt）";
                default -> type + " 格式（." + type + "）";
            };
            sb.append("- ").append(label).append("\n");
        }

        return sb.toString();
    }

    private Flux<StreamEventDTO> simulateStreaming(String text) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }

        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int chunkSize = 2 + (int)(Math.random() * 3); // 2-4 字符
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            i = end;
        }

        return Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(20))
                .map(chunk -> StreamEventDTO.builder()
                        .type("token")
                        .content(chunk)
                        .build());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
