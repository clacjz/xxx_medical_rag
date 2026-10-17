package com.clacjz.medical.rag.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIMetricsService 单元测试")
class AIMetricsServiceTest {

    private MeterRegistry registry;
    private AIMetricsService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new AIMetricsService(registry);
    }

    @Nested
    @DisplayName("isRetrievalQualityAcceptable")
    class IsRetrievalQualityAcceptableTests {

        @Test
        @DisplayName("所有指标达标时返回 true")
        void shouldReturnTrueWhenAllMetricsMet() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.80)
                    .precisionAtK(0.70)
                    .mrr(0.75)
                    .ndcgAtK(0.70)
                    .build();

            assertTrue(service.isRetrievalQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("刚好达到阈值返回 true")
        void shouldReturnTrueAtThreshold() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.75)
                    .precisionAtK(0.60)
                    .mrr(0.70)
                    .ndcgAtK(0.65)
                    .build();

            assertTrue(service.isRetrievalQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("Recall 低于阈值返回 false")
        void shouldReturnFalseWhenRecallTooLow() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.5)
                    .precisionAtK(0.7)
                    .mrr(0.75)
                    .ndcgAtK(0.7)
                    .build();

            assertFalse(service.isRetrievalQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("Precision 低于阈值返回 false")
        void shouldReturnFalseWhenPrecisionTooLow() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.8)
                    .precisionAtK(0.4)
                    .mrr(0.75)
                    .ndcgAtK(0.7)
                    .build();

            assertFalse(service.isRetrievalQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("MRR 低于阈值返回 false")
        void shouldReturnFalseWhenMrrTooLow() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.8)
                    .precisionAtK(0.7)
                    .mrr(0.5)
                    .ndcgAtK(0.7)
                    .build();

            assertFalse(service.isRetrievalQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("NDCG 低于阈值返回 false")
        void shouldReturnFalseWhenNdcgTooLow() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.8)
                    .precisionAtK(0.7)
                    .mrr(0.75)
                    .ndcgAtK(0.3)
                    .build();

            assertFalse(service.isRetrievalQualityAcceptable(metrics));
        }
    }

    @Nested
    @DisplayName("isGenerationQualityAcceptable")
    class IsGenerationQualityAcceptableTests {

        @Test
        @DisplayName("所有指标达标时返回 true")
        void shouldReturnTrueWhenAllMetricsMet() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(85)
                    .hallucinationRate(0.04)
                    .citationAccuracy(0.92)
                    .build();

            assertTrue(service.isGenerationQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("刚好达到阈值返回 true")
        void shouldReturnTrueAtThreshold() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(80)
                    .hallucinationRate(0.05)
                    .citationAccuracy(0.90)
                    .build();

            assertTrue(service.isGenerationQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("Quality 低于阈值返回 false")
        void shouldReturnFalseWhenQualityTooLow() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(60)
                    .hallucinationRate(0.04)
                    .citationAccuracy(0.92)
                    .build();

            assertFalse(service.isGenerationQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("Hallucination 超过阈值返回 false")
        void shouldReturnFalseWhenHallucinationTooHigh() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(85)
                    .hallucinationRate(0.10)
                    .citationAccuracy(0.92)
                    .build();

            assertFalse(service.isGenerationQualityAcceptable(metrics));
        }

        @Test
        @DisplayName("Citation Accuracy 低于阈值返回 false")
        void shouldReturnFalseWhenCitationTooLow() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(85)
                    .hallucinationRate(0.04)
                    .citationAccuracy(0.7)
                    .build();

            assertFalse(service.isGenerationQualityAcceptable(metrics));
        }
    }

    @Nested
    @DisplayName("记录指标")
    class RecordMetricsTests {

        @Test
        @DisplayName("记录检索指标不抛异常")
        void shouldRecordRetrievalMetrics() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.8)
                    .precisionAtK(0.7)
                    .mrr(0.75)
                    .ndcgAtK(0.7)
                    .build();

            assertDoesNotThrow(() -> service.recordRetrievalMetrics(metrics));
        }

        @Test
        @DisplayName("记录生成指标不抛异常")
        void shouldRecordGenerationMetrics() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(85)
                    .hallucinationRate(0.04)
                    .citationAccuracy(0.92)
                    .build();

            assertDoesNotThrow(() -> service.recordGenerationMetrics(metrics));
        }

        @Test
        @DisplayName("记录 embedding 延迟不抛异常")
        void shouldRecordEmbeddingLatency() {
            assertDoesNotThrow(() -> service.recordEmbeddingLatency(50));
        }

        @Test
        @DisplayName("记录 vector search 延迟不抛异常")
        void shouldRecordVectorSearchLatency() {
            assertDoesNotThrow(() -> service.recordVectorSearchLatency(20));
        }

        @Test
        @DisplayName("记录 LLM 生成延迟不抛异常")
        void shouldRecordLlmGenerateLatency() {
            assertDoesNotThrow(() -> service.recordLlmGenerateLatency(2000));
        }

        @Test
        @DisplayName("记录总查询延迟不抛异常")
        void shouldRecordTotalQueryLatency() {
            assertDoesNotThrow(() -> service.recordTotalQueryLatency(3000));
        }

        @Test
        @DisplayName("记录首 token 延迟不抛异常")
        void shouldRecordFirstTokenLatency() {
            assertDoesNotThrow(() -> service.recordFirstTokenLatency(500));
        }

        @Test
        @DisplayName("记录追踪覆盖率不抛异常")
        void shouldRecordTracingCoverage() {
            assertDoesNotThrow(() -> service.recordTracingCoverage(95.0));
        }
    }

    @Nested
    @DisplayName("getSnapshot")
    class GetSnapshotTests {

        @Test
        @DisplayName("返回 MetricsSnapshot 实例")
        void shouldReturnSnapshot() {
            AIMetricsService.MetricsSnapshot snapshot = service.getSnapshot();
            assertNotNull(snapshot);
        }

        @Test
        @DisplayName("初始 snapshot 默认值大部分为 0（tracingCoverage 默认 95）")
        void shouldReturnZeroDefaults() {
            AIMetricsService.MetricsSnapshot snapshot = service.getSnapshot();
            assertEquals(0.0, snapshot.getRetrievalRecall());
            assertEquals(0.0, snapshot.getRetrievalPrecision());
            assertEquals(0.0, snapshot.getRetrievalMrr());
            assertEquals(0.0, snapshot.getRetrievalNdcg());
            assertEquals(0.0, snapshot.getGenerationQualityScore());
            assertEquals(0.0, snapshot.getHallucinationRate());
            assertEquals(0.0, snapshot.getCitationAccuracy());
            // tracingCoverage 默认值为 95
            assertEquals(95.0, snapshot.getTracingCoverage());
        }
    }

    @Nested
    @DisplayName("getMeterRegistry")
    class GetMeterRegistryTests {

        @Test
        @DisplayName("返回注入的 registry")
        void shouldReturnRegistry() {
            assertSame(registry, service.getMeterRegistry());
        }
    }
}
