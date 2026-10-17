package com.clacjz.medical.rag.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AITracingService 单元测试")
class AITracingServiceTest {

    private AITracingService service;

    @BeforeEach
    void setUp() {
        // 使用自注入（@Lazy self）以启用 AOP 增强
        AITracingService self = null; // 简化测试
        try {
            java.lang.reflect.Field selfField = AITracingService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            // 通过 reflection 创建一个简单的实例（仅用于 builder 测试）
            service = new AITracingService(self, null);
        } catch (Exception e) {
            // 忽略
        }
    }

    @Nested
    @DisplayName("RetrievalMetrics - 内部类")
    class RetrievalMetricsTests {

        @Test
        @DisplayName("可以正确构建")
        void shouldBuildRetrievalMetrics() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder()
                    .recallAtK(0.8)
                    .precisionAtK(0.7)
                    .mrr(0.75)
                    .ndcgAtK(0.7)
                    .hitCount(8L)
                    .retrievedCount(10)
                    .relevantCount(10)
                    .build();

            assertEquals(0.8, metrics.getRecallAtK());
            assertEquals(0.7, metrics.getPrecisionAtK());
            assertEquals(0.75, metrics.getMrr());
            assertEquals(0.7, metrics.getNdcgAtK());
            assertEquals(8L, metrics.getHitCount());
            assertEquals(10, metrics.getRetrievedCount());
            assertEquals(10, metrics.getRelevantCount());
        }

        @Test
        @DisplayName("默认 builder 值都为 0")
        void shouldDefaultToZero() {
            AITracingService.RetrievalMetrics metrics = AITracingService.RetrievalMetrics.builder().build();

            assertEquals(0.0, metrics.getRecallAtK());
            assertEquals(0.0, metrics.getPrecisionAtK());
            assertEquals(0.0, metrics.getMrr());
            assertEquals(0.0, metrics.getNdcgAtK());
            assertEquals(0L, metrics.getHitCount());
            assertEquals(0, metrics.getRetrievedCount());
            assertEquals(0, metrics.getRelevantCount());
        }
    }

    @Nested
    @DisplayName("GenerationMetrics - 内部类")
    class GenerationMetricsTests {

        @Test
        @DisplayName("可以正确构建")
        void shouldBuildGenerationMetrics() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder()
                    .qualityScore(85)
                    .hallucinationRate(0.04)
                    .citationAccuracy(0.92)
                    .promptTokens(500)
                    .build();

            assertEquals(85, metrics.getQualityScore());
            assertEquals(0.04, metrics.getHallucinationRate());
            assertEquals(0.92, metrics.getCitationAccuracy());
            assertEquals(500, metrics.getPromptTokens());
        }

        @Test
        @DisplayName("默认 builder 值都为 0")
        void shouldDefaultToZero() {
            AITracingService.GenerationMetrics metrics = AITracingService.GenerationMetrics.builder().build();

            assertEquals(0.0, metrics.getQualityScore());
            assertEquals(0.0, metrics.getHallucinationRate());
            assertEquals(0.0, metrics.getCitationAccuracy());
            assertEquals(0, metrics.getPromptTokens());
        }
    }

    @Nested
    @DisplayName("AISpan - 内部类")
    class AISpanTests {

        @Test
        @DisplayName("可以正确构建")
        void shouldBuildAISpan() {
            java.time.Instant now = java.time.Instant.now();
            AISpan span = AISpan.builder()
                    .traceId("trace-1")
                    .spanId("span-1")
                    .operationName("search")
                    .spanType("RETRIEVAL")
                    .startTime(now)
                    .durationMs(100L)
                    .build();

            assertEquals("trace-1", span.getTraceId());
            assertEquals("span-1", span.getSpanId());
            assertEquals("search", span.getOperationName());
            assertEquals("RETRIEVAL", span.getSpanType());
            assertEquals(100L, span.getDurationMs());
        }
    }

    @Nested
    @DisplayName("calculateRetrievalMetrics - 检索质量计算")
    class CalculateRetrievalMetricsTests {

        @Test
        @DisplayName("空 relevant 和 retrieved 返回 0")
        void shouldReturnZeroForEmpty() {
            AITracingService.RetrievalMetrics metrics = service.calculateRetrievalMetrics(
                    List.of(), List.of(), 5);
            assertEquals(0.0, metrics.getRecallAtK());
            assertEquals(0.0, metrics.getPrecisionAtK());
        }

        @Test
        @DisplayName("完全匹配返回高分")
        void shouldReturnHighScoreForFullMatch() {
            AITracingService.RetrievalMetrics metrics = service.calculateRetrievalMetrics(
                    List.of("d1", "d2", "d3"),
                    List.of("d1", "d2", "d3", "d4", "d5"),
                    5);

            assertEquals(1.0, metrics.getRecallAtK());
            assertEquals(0.6, metrics.getPrecisionAtK()); // 3/5
            assertEquals(3L, metrics.getHitCount()); // 3 个相关文档被命中
        }

        @Test
        @DisplayName("部分匹配返回中间分数")
        void shouldReturnPartialScore() {
            AITracingService.RetrievalMetrics metrics = service.calculateRetrievalMetrics(
                    List.of("d1", "d2", "d3", "d4", "d5"),
                    List.of("d1", "x", "y"),
                    3);

            // recall = 1/5 = 0.2
            assertEquals(0.2, metrics.getRecallAtK(), 0.01);
            // precision = 1/3 = 0.333
            assertEquals(0.333, metrics.getPrecisionAtK(), 0.01);
            assertEquals(1L, metrics.getHitCount());
        }
    }

    @Nested
    @DisplayName("calculateGenerationMetrics - 生成质量计算")
    class CalculateGenerationMetricsTests {

        @Test
        @DisplayName("答案为空时返回基础分")
        void shouldReturnBaseScoreForEmptyAnswer() {
            AITracingService.GenerationMetrics metrics = service.calculateGenerationMetrics("", List.of("c1"));
            assertNotNull(metrics);
        }

        @Test
        @DisplayName("有答案和上下文时正常计算")
        void shouldCalculateWithAnswerAndContext() {
            AITracingService.GenerationMetrics metrics = service.calculateGenerationMetrics(
                    "The answer based on the document",
                    List.of("context 1", "context 2")
            );

            assertNotNull(metrics);
            assertTrue(metrics.getQualityScore() >= 0);
        }
    }

    @Nested
    @DisplayName("getTracingCoverage - 追踪覆盖率")
    class GetTracingCoverageTests {

        @Test
        @DisplayName("返回覆盖率数值")
        void shouldReturnCoverage() {
            double coverage = service.getTracingCoverage();
            assertTrue(coverage >= 0);
            assertTrue(coverage <= 100);
        }
    }

    @Nested
    @DisplayName("Span 操作")
    class SpanOperationTests {

        @Test
        @DisplayName("startSpan 返回 AISpan")
        void shouldStartSpan() {
            AISpan span = service.startSpan("trace-1", "search", "RETRIEVAL");
            assertNotNull(span);
            assertEquals("trace-1", span.getTraceId());
        }

        @Test
        @DisplayName("endSpan 完成 span")
        void shouldEndSpan() {
            AISpan span = service.startSpan("trace-2", "search", "RETRIEVAL");
            AISpan ended = service.endSpan(span);
            assertNotNull(ended);
            assertNotNull(ended.getEndTime());
        }
    }
}
