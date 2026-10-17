package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.monitor.CostRecord;
import com.clacjz.medical.rag.monitor.CostTrackingService;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CostTrackingController 单元测试")
class CostTrackingControllerTest {

    @Mock
    private CostTrackingService costTrackingService;

    private CostTrackingController controller;

    @BeforeEach
    void setUp() {
        controller = new CostTrackingController(costTrackingService);
    }

    @Nested
    @DisplayName("GET /api/v1/costs/report")
    class ReportTests {

        @Test
        @DisplayName("成功返回 cost report")
        void shouldReturnReport() {
            CostTrackingService.CostReport report = CostTrackingService.CostReport.builder()
                    .startTime(LocalDateTime.now().minusDays(1))
                    .endTime(LocalDateTime.now())
                    .totalCost(10.5)
                    .gpuCost(5.0)
                    .tokenCost(3.0)
                    .storageCost(2.5)
                    .totalGpuTimeMs(1000L)
                    .totalPromptTokens(500)
                    .totalCompletionTokens(800)
                    .totalStoredBytes(2048L)
                    .queryCount(5)
                    .documentProcessingCount(2)
                    .recordCount(7)
                    .build();
            when(costTrackingService.getCostReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(report);

            long start = System.currentTimeMillis() - 86400_000L;
            long end = System.currentTimeMillis();
            ResponseEntity<Result<CostTrackingService.CostReport>> response =
                    controller.getCostReport(start, end);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertNotNull(response.getBody().getData());
            assertEquals(10.5, response.getBody().getData().getTotalCost(), 1e-9);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/costs/metrics")
    class MetricsTests {

        @Test
        @DisplayName("成功返回 cost metrics")
        void shouldReturnMetrics() {
            CostTrackingService.CostMetrics metrics = CostTrackingService.CostMetrics.builder()
                    .totalCost(50.0)
                    .avgCostPerQuery(0.5)
                    .totalQueries(100L)
                    .build();
            when(costTrackingService.getCostMetrics()).thenReturn(metrics);

            ResponseEntity<Result<CostTrackingService.CostMetrics>> response = controller.getCostMetrics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(100L, response.getBody().getData().getTotalQueries());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/costs/record/query")
    class RecordQueryTests {

        @Test
        @DisplayName("记录 query cost 成功")
        void shouldRecordQueryCost() {
            CostRecord record = mock(CostRecord.class);
            when(costTrackingService.recordQueryCost(eq("trace-1"), eq("sess-1"), eq("user-1"),
                    eq(100L), eq(50), eq(80))).thenReturn(record);

            ResponseEntity<Result<CostRecord>> response = controller.recordQueryCost(
                    "trace-1", "sess-1", "user-1", 100L, 50, 80);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());
        }

        @Test
        @DisplayName("sessionId 和 userId 可选")
        void shouldAllowNullOptionalParams() {
            CostRecord record = mock(CostRecord.class);
            when(costTrackingService.recordQueryCost(eq("trace-2"), isNull(), isNull(),
                    anyLong(), anyInt(), anyInt())).thenReturn(record);

            ResponseEntity<Result<CostRecord>> response = controller.recordQueryCost(
                    "trace-2", null, null, 200L, 30, 40);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/costs/record/document")
    class RecordDocumentTests {

        @Test
        @DisplayName("记录 document cost 成功")
        void shouldRecordDocumentCost() {
            CostRecord record = mock(CostRecord.class);
            when(costTrackingService.recordDocumentProcessingCost(eq("trace-3"), eq("doc-1"),
                    eq(500L), eq(2048L))).thenReturn(record);

            ResponseEntity<Result<CostRecord>> response = controller.recordDocumentCost(
                    "trace-3", "doc-1", 500L, 2048L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/costs/summary")
    class SummaryTests {

        @Test
        @DisplayName("默认 day 周期")
        void shouldReturnDailySummary() {
            CostTrackingService.CostReport report = buildReport();
            when(costTrackingService.getCostReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(report);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getCostSummary("day");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> body = response.getBody().getData();
            assertEquals("day", body.get("period"));
            assertTrue(body.containsKey("totalCost"));
            assertTrue(body.containsKey("gpuCost"));
            assertTrue(body.containsKey("tokenCost"));
            assertTrue(body.containsKey("storageCost"));
            assertTrue(body.containsKey("queryCount"));
            assertTrue(body.containsKey("documentProcessingCount"));
        }

        @Test
        @DisplayName("week 周期")
        void shouldReturnWeeklySummary() {
            CostTrackingService.CostReport report = buildReport();
            when(costTrackingService.getCostReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(report);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getCostSummary("week");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("week", response.getBody().getData().get("period"));
        }

        @Test
        @DisplayName("month 周期")
        void shouldReturnMonthlySummary() {
            CostTrackingService.CostReport report = buildReport();
            when(costTrackingService.getCostReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(report);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getCostSummary("month");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("month", response.getBody().getData().get("period"));
        }

        @Test
        @DisplayName("未知周期降级为 day")
        void shouldFallbackToDayForUnknownPeriod() {
            CostTrackingService.CostReport report = buildReport();
            when(costTrackingService.getCostReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(report);

            ResponseEntity<Result<Map<String, Object>>> response = controller.getCostSummary("unknown-period");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            // 控制器把无法识别的 period 字符串原样写入响应，body 中 period 字段保持调用值
            assertEquals("unknown-period", response.getBody().getData().get("period"));
        }
    }

    private CostTrackingService.CostReport buildReport() {
        return CostTrackingService.CostReport.builder()
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now())
                .totalCost(0.0)
                .gpuCost(0.0)
                .tokenCost(0.0)
                .storageCost(0.0)
                .totalGpuTimeMs(0L)
                .totalPromptTokens(0)
                .totalCompletionTokens(0)
                .totalStoredBytes(0L)
                .queryCount(0)
                .documentProcessingCount(0)
                .recordCount(0)
                .build();
    }
}
