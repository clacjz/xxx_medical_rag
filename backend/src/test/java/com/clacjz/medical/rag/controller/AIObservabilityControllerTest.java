package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AITracingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AIObservabilityController 单元测试")
class AIObservabilityControllerTest {

    @Mock
    private AIMetricsService metricsService;

    @Mock
    private AITracingService tracingService;

    private AIObservabilityController controller;

    @BeforeEach
    void setUp() {
        controller = new AIObservabilityController(metricsService, tracingService);
    }

    /** 通过 builder 构造 MetricsSnapshot */
    private AIMetricsService.MetricsSnapshot snapshot(double recall, double precision, double mrr, double ndcg,
                                                      double qualityScore, double hallucination, double citation,
                                                      double coverage) {
        return AIMetricsService.MetricsSnapshot.builder()
                .retrievalRecall(recall)
                .retrievalPrecision(precision)
                .retrievalMrr(mrr)
                .retrievalNdcg(ndcg)
                .generationQualityScore(qualityScore)
                .hallucinationRate(hallucination)
                .citationAccuracy(citation)
                .tracingCoverage(coverage)
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/ai-observability/metrics")
    class MetricsTests {

        @Test
        @DisplayName("返回 metrics snapshot")
        void shouldReturnSnapshot() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 96));

            ResponseEntity<Result<AIMetricsService.MetricsSnapshot>> response = controller.getMetrics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(0.8, response.getBody().getData().getRetrievalRecall(), 1e-9);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai-observability/metrics/retrieval")
    class RetrievalMetricsTests {

        @Test
        @DisplayName("正常指标返回 OK 状态")
        void shouldReturnOkWhenAcceptable() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 96));
            when(metricsService.isRetrievalQualityAcceptable(any())).thenReturn(true);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getRetrievalMetrics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody().getData();
            assertEquals("OK", body.get("status"));
            assertTrue(body.containsKey("target"));
        }

        @Test
        @DisplayName("不可接受时返回 DEGRADED")
        void shouldReturnDegradedWhenUnacceptable() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.5, 0.4, 0.3, 0.35, 50, 0.1, 0.5, 90));
            when(metricsService.isRetrievalQualityAcceptable(any())).thenReturn(false);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getRetrievalMetrics();

            assertEquals("DEGRADED", response.getBody().getData().get("status"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai-observability/metrics/generation")
    class GenerationMetricsTests {

        @Test
        @DisplayName("正常指标返回 OK 状态")
        void shouldReturnOkWhenAcceptable() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 96));
            when(metricsService.isGenerationQualityAcceptable(any())).thenReturn(true);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getGenerationMetrics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("OK", response.getBody().getData().get("status"));
        }

        @Test
        @DisplayName("不可接受时返回 DEGRADED")
        void shouldReturnDegradedWhenUnacceptable() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 50, 0.1, 0.5, 96));
            when(metricsService.isGenerationQualityAcceptable(any())).thenReturn(false);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getGenerationMetrics();

            assertEquals("DEGRADED", response.getBody().getData().get("status"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai-observability/tracing/coverage")
    class TracingCoverageTests {

        @Test
        @DisplayName("覆盖率 >= 95 返回 OK")
        void shouldReturnOkWhenCoverageSufficient() {
            when(tracingService.getTracingCoverage()).thenReturn(98.0);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getTracingCoverage();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody().getData();
            assertEquals(98.0, body.get("coverage"));
            assertEquals("OK", body.get("status"));
        }

        @Test
        @DisplayName("覆盖率 < 95 返回 INSUFFICIENT")
        void shouldReturnInsufficientWhenLow() {
            when(tracingService.getTracingCoverage()).thenReturn(80.0);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getTracingCoverage();

            assertEquals("INSUFFICIENT", response.getBody().getData().get("status"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai-observability/alerts")
    class AlertsTests {

        @Test
        @DisplayName("全部指标达标时无告警")
        void shouldReturnNoAlertsWhenAllOk() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 96));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().getData().isEmpty());
        }

        @Test
        @DisplayName("Recall 低于阈值时触发 RETRIEVAL_QUALITY 告警")
        void shouldAlertOnLowRecall() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.5, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 96));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            List<Map<String, Object>> alerts = response.getBody().getData();
            assertEquals(1, alerts.size());
            assertEquals("RETRIEVAL_QUALITY", alerts.get(0).get("type"));
            assertEquals("WARNING", alerts.get(0).get("severity"));
        }

        @Test
        @DisplayName("幻觉率超阈值时触发 CRITICAL 告警")
        void shouldAlertOnHighHallucination() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.15, 0.92, 96));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            assertTrue(response.getBody().getData().stream()
                    .anyMatch(a -> "HALLUCINATION".equals(a.get("type")) && "CRITICAL".equals(a.get("severity"))));
        }

        @Test
        @DisplayName("生成质量评分低时触发 GENERATION_QUALITY 告警")
        void shouldAlertOnLowQualityScore() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 60, 0.03, 0.92, 96));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            assertTrue(response.getBody().getData().stream()
                    .anyMatch(a -> "GENERATION_QUALITY".equals(a.get("type"))));
        }

        @Test
        @DisplayName("追踪覆盖率低时触发 TRACING_COVERAGE 告警")
        void shouldAlertOnLowTracingCoverage() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.8, 0.7, 0.75, 0.78, 85, 0.03, 0.92, 70));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            assertTrue(response.getBody().getData().stream()
                    .anyMatch(a -> "TRACING_COVERAGE".equals(a.get("type"))));
        }

        @Test
        @DisplayName("多个指标同时不达标时返回多条告警")
        void shouldReturnMultipleAlerts() {
            when(metricsService.getSnapshot()).thenReturn(
                    snapshot(0.5, 0.3, 0.2, 0.2, 50, 0.15, 0.5, 80));

            ResponseEntity<Result<List<Map<String, Object>>>> response = controller.getAlerts();

            assertEquals(4, response.getBody().getData().size());
        }
    }
}
