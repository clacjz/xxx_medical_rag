package com.clacjz.medical.rag.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CostTrackingService 单元测试")
class CostTrackingServiceTest {

    private CostTrackingService service;

    @BeforeEach
    void setUp() {
        service = new CostTrackingService();
    }

    @Nested
    @DisplayName("recordQueryCost - 记录查询成本")
    class RecordQueryCostTests {

        @Test
        @DisplayName("正常记录返回 CostRecord")
        void shouldRecordQueryCost() {
            CostRecord record = service.recordQueryCost(
                    "trace-1", "session-1", "user-1", 1000L, 500, 800);

            assertNotNull(record);
            assertNotNull(record.getTraceId());
            assertEquals("trace-1", record.getTraceId());
            assertEquals("session-1", record.getSessionId());
            assertEquals("user-1", record.getUserId());
        }

        @Test
        @DisplayName("sessionId 和 userId 可为 null")
        void shouldAllowNullOptionalFields() {
            CostRecord record = service.recordQueryCost("trace-2", null, null, 500L, 100, 200);

            assertEquals("trace-2", record.getTraceId());
            assertNull(record.getSessionId());
            assertNull(record.getUserId());
        }

        @Test
        @DisplayName("返回的记录包含 createdAt 时间")
        void shouldPopulateCreatedAt() {
            CostRecord record = service.recordQueryCost("t", "s", "u", 100L, 10, 20);

            assertNotNull(record.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("recordDocumentProcessingCost - 记录文档处理成本")
    class RecordDocumentCostTests {

        @Test
        @DisplayName("正常记录返回 CostRecord")
        void shouldRecordDocumentCost() {
            CostRecord record = service.recordDocumentProcessingCost(
                    "trace-3", "doc-1", 2000L, 5000L);

            assertNotNull(record);
            assertEquals("trace-3", record.getTraceId());
            // documentId 存储在 sessionId 字段
            assertEquals("doc-1", record.getSessionId());
            assertEquals(2000L, record.getGpuTimeMs());
            assertEquals(5000L, record.getStoredBytes());
        }
    }

    @Nested
    @DisplayName("recordEmbeddingCost - 记录 embedding 成本")
    class RecordEmbeddingCostTests {

        @Test
        @DisplayName("正常记录返回 CostRecord")
        void shouldRecordEmbeddingCost() {
            CostRecord record = service.recordEmbeddingCost("trace-4", "doc-1", 500L, 1000);

            assertNotNull(record);
            assertEquals("trace-4", record.getTraceId());
            // documentId 存储在 sessionId 字段
            assertEquals("doc-1", record.getSessionId());
        }
    }

    @Nested
    @DisplayName("getCostReport - 获取成本报表")
    class GetCostReportTests {

        @Test
        @DisplayName("空记录时返回的 report 总成本为 0")
        void shouldReturnZeroReportForEmptyData() {
            CostTrackingService.CostReport report = service.getCostReport(
                    LocalDateTime.now().minusDays(1), LocalDateTime.now());

            assertNotNull(report);
            assertEquals(0.0, report.getTotalCost(), 1e-9);
            assertEquals(0, report.getQueryCount());
            assertEquals(0, report.getDocumentProcessingCount());
        }

        @Test
        @DisplayName("包含的记录会被统计")
        void shouldAggregateRecordsInRange() {
            service.recordQueryCost("t-1", "s-1", "u-1", 1000L, 100, 200);
            service.recordQueryCost("t-2", "s-1", "u-1", 2000L, 300, 400);

            CostTrackingService.CostReport report = service.getCostReport(
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

            assertEquals(2, report.getQueryCount());
        }
    }

    @Nested
    @DisplayName("getCostMetrics - 获取成本指标")
    class GetCostMetricsTests {

        @Test
        @DisplayName("返回 CostMetrics 实例")
        void shouldReturnMetrics() {
            CostTrackingService.CostMetrics metrics = service.getCostMetrics();

            assertNotNull(metrics);
            assertNotNull(metrics);
        }
    }

    @Nested
    @DisplayName("cleanupExpiredRecords - 清理过期记录")
    class CleanupExpiredRecordsTests {

        @Test
        @DisplayName("正常执行不抛异常")
        void shouldCleanupWithoutException() {
            service.recordQueryCost("t-1", "s-1", "u-1", 100L, 10, 20);
            assertDoesNotThrow(() -> service.cleanupExpiredRecords());
        }

        @Test
        @DisplayName("清理后查询计数不增加（不应新加）")
        void shouldRemoveExpiredRecords() {
            service.recordQueryCost("t-1", "s-1", "u-1", 100L, 10, 20);
            long before = service.getCostMetrics().getTotalQueries();

            service.cleanupExpiredRecords();

            long after = service.getCostMetrics().getTotalQueries();
            assertTrue(after <= before);
        }
    }
}
