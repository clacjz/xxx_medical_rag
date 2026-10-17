package com.clacjz.medical.rag.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI链路质量指标服务
 *
 * 设计依据: HLD Section 3.2 质量评估指标定义
 *
 * Prometheus指标定义:
 * - AI retrieval_quality recall/precision/mrr/ndcg
 * - AI generation_quality quality_score/hallucination_rate/citation_accuracy
 * - AI latency embedding/vector_search/llm_generate
 */
@Slf4j
@Service
public class AIMetricsService {

    private final MeterRegistry meterRegistry;

    // 检索质量指标
    private final Counter retrievalTotal;
    private final Counter retrievalSuccess;
    private final AtomicLong lastRecall = new AtomicLong(0);
    private final AtomicLong lastPrecision = new AtomicLong(0);
    private final AtomicLong lastMrr = new AtomicLong(0);
    private final AtomicLong lastNdcg = new AtomicLong(0);

    // 生成质量指标
    private final Counter generationTotal;
    private final Counter generationSuccess;
    private final AtomicLong lastQualityScore = new AtomicLong(0);
    private final AtomicLong lastHallucinationRate = new AtomicLong(0);
    private final AtomicLong lastCitationAccuracy = new AtomicLong(0);

    // 延迟指标
    private final Timer embeddingLatency;
    private final Timer vectorSearchLatency;
    private final Timer llmGenerateLatency;
    private final Timer totalQueryLatency;
    private final Timer firstTokenLatency;

    // 追踪覆盖率
    private final AtomicLong tracingCoverage = new AtomicLong(95);

    public AIMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 检索质量指标
        this.retrievalTotal = Counter.builder("ai_retrieval_total")
                .description("Total number of retrieval requests")
                .register(meterRegistry);

        this.retrievalSuccess = Counter.builder("ai_retrieval_success")
                .description("Number of successful retrieval requests")
                .register(meterRegistry);

        // 生成质量指标
        this.generationTotal = Counter.builder("ai_generation_total")
                .description("Total number of generation requests")
                .register(meterRegistry);

        this.generationSuccess = Counter.builder("ai_generation_success")
                .description("Number of successful generation requests")
                .register(meterRegistry);

        // 注册Gauges用于质量指标
        Gauge.builder("ai_retrieval_quality_recall", lastRecall, AtomicLong::doubleValue)
                .description("Retrieval Recall@5")
                .register(meterRegistry);

        Gauge.builder("ai_retrieval_quality_precision", lastPrecision, AtomicLong::doubleValue)
                .description("Retrieval Precision@5")
                .register(meterRegistry);

        Gauge.builder("ai_retrieval_quality_mrr", lastMrr, AtomicLong::doubleValue)
                .description("Retrieval MRR")
                .register(meterRegistry);

        Gauge.builder("ai_retrieval_quality_ndcg", lastNdcg, AtomicLong::doubleValue)
                .description("Retrieval NDCG@5")
                .register(meterRegistry);

        Gauge.builder("ai_generation_quality_score", lastQualityScore, AtomicLong::doubleValue)
                .description("Generation quality score (0-100)")
                .register(meterRegistry);

        Gauge.builder("ai_generation_hallucination_rate", lastHallucinationRate, AtomicLong::doubleValue)
                .description("Generation hallucination rate (0-1)")
                .register(meterRegistry);

        Gauge.builder("ai_generation_citation_accuracy", lastCitationAccuracy, AtomicLong::doubleValue)
                .description("Generation citation accuracy (0-1)")
                .register(meterRegistry);

        Gauge.builder("ai_tracing_coverage", tracingCoverage, AtomicLong::doubleValue)
                .description("AI链路追踪覆盖率 (0-100)")
                .register(meterRegistry);

        // 延迟指标
        this.embeddingLatency = Timer.builder("ai_latency_embedding")
                .description("Embedding query latency")
                .register(meterRegistry);

        this.vectorSearchLatency = Timer.builder("ai_latency_vector_search")
                .description("Vector search latency")
                .register(meterRegistry);

        this.llmGenerateLatency = Timer.builder("ai_latency_llm_generate")
                .description("LLM generation latency")
                .register(meterRegistry);

        this.totalQueryLatency = Timer.builder("ai_latency_total_query")
                .description("Total RAG query latency")
                .register(meterRegistry);

        this.firstTokenLatency = Timer.builder("ai_latency_first_token")
                .description("Time to first token (TTFT)")
                .register(meterRegistry);
    }

    /**
     * 记录检索质量指标
     *
     * 设计要求: Recall@5 ≥ 0.75, MRR ≥ 0.70, Precision@5 ≥ 0.60, NDCG@5 ≥ 0.65
     */
    public void recordRetrievalMetrics(AITracingService.RetrievalMetrics metrics) {
        retrievalTotal.increment();
        retrievalSuccess.increment();

        lastRecall.set((long) (metrics.getRecallAtK() * 100));
        lastPrecision.set((long) (metrics.getPrecisionAtK() * 100));
        lastMrr.set((long) (metrics.getMrr() * 100));
        lastNdcg.set((long) (metrics.getNdcgAtK() * 100));

        log.debug("[AIMetrics] Recorded retrieval metrics: recall={}, precision={}, mrr={}, ndcg={}",
                metrics.getRecallAtK(), metrics.getPrecisionAtK(), metrics.getMrr(), metrics.getNdcgAtK());
    }

    /**
     * 记录生成质量指标
     *
     * 设计要求: 质量评分 ≥ 80分, 幻觉率 ≤ 5%, 引用准确率 ≥ 90%
     */
    public void recordGenerationMetrics(AITracingService.GenerationMetrics metrics) {
        generationTotal.increment();
        generationSuccess.increment();

        lastQualityScore.set((long) metrics.getQualityScore());
        lastHallucinationRate.set((long) (metrics.getHallucinationRate() * 100));
        lastCitationAccuracy.set((long) (metrics.getCitationAccuracy() * 100));

        log.debug("[AIMetrics] Recorded generation metrics: quality={}, hallucination={}, citation={}",
                metrics.getQualityScore(), metrics.getHallucinationRate(), metrics.getCitationAccuracy());
    }

    /**
     * 记录延迟
     */
    public void recordEmbeddingLatency(long durationMs) {
        embeddingLatency.record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordVectorSearchLatency(long durationMs) {
        vectorSearchLatency.record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordLlmGenerateLatency(long durationMs) {
        llmGenerateLatency.record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordTotalQueryLatency(long durationMs) {
        totalQueryLatency.record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordFirstTokenLatency(long durationMs) {
        firstTokenLatency.record(java.time.Duration.ofMillis(durationMs));
    }

    /**
     * 记录追踪覆盖率
     */
    public void recordTracingCoverage(double coverage) {
        tracingCoverage.set((long) coverage);
    }

    /**
     * 检查质量阈值
     */
    public boolean isRetrievalQualityAcceptable(AITracingService.RetrievalMetrics metrics) {
        return metrics.getRecallAtK() >= 0.75 &&
                metrics.getPrecisionAtK() >= 0.60 &&
                metrics.getMrr() >= 0.70 &&
                metrics.getNdcgAtK() >= 0.65;
    }

    public boolean isGenerationQualityAcceptable(AITracingService.GenerationMetrics metrics) {
        return metrics.getQualityScore() >= 80 &&
                metrics.getHallucinationRate() <= 0.05 &&
                metrics.getCitationAccuracy() >= 0.90;
    }

    /**
     * 获取当前指标快照
     */
    public MetricsSnapshot getSnapshot() {
        return MetricsSnapshot.builder()
                .retrievalRecall(lastRecall.doubleValue() / 100)
                .retrievalPrecision(lastPrecision.doubleValue() / 100)
                .retrievalMrr(lastMrr.doubleValue() / 100)
                .retrievalNdcg(lastNdcg.doubleValue() / 100)
                .generationQualityScore(lastQualityScore.doubleValue())
                .hallucinationRate(lastHallucinationRate.doubleValue() / 100)
                .citationAccuracy(lastCitationAccuracy.doubleValue() / 100)
                .tracingCoverage(tracingCoverage.doubleValue())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class MetricsSnapshot {
        private double retrievalRecall;
        private double retrievalPrecision;
        private double retrievalMrr;
        private double retrievalNdcg;
        private double generationQualityScore;
        private double hallucinationRate;
        private double citationAccuracy;
        private double tracingCoverage;
    }

    /**
     * 获取MeterRegistry用于外部注册指标
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}