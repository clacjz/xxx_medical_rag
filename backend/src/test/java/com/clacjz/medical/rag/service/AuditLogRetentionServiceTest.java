package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.mapper.PermissionAuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogRetentionService 单元测试")
class AuditLogRetentionServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private PermissionAuditLogMapper permissionAuditLogMapper;

    @InjectMocks
    private AuditLogRetentionService auditLogRetentionService;

    @Nested
    @DisplayName("getRetentionStatus - 获取保留状态")
    class GetRetentionStatusTests {

        @Test
        @DisplayName("返回正确的保留状态信息")
        void shouldReturnCorrectRetentionStatus() {
            when(auditLogMapper.countByCategory("DOCUMENT_OPERATION")).thenReturn(100L);
            when(auditLogMapper.countByCategory("SYSTEM_CONFIG")).thenReturn(50L);
            when(auditLogMapper.countByCategory("USER_MANAGEMENT")).thenReturn(25L);
            when(permissionAuditLogMapper.countAll()).thenReturn(200L);

            AuditLogRetentionService.RetentionStatus status = auditLogRetentionService.getRetentionStatus();

            assertNotNull(status);
            assertEquals(100L, status.getDocumentOperationCount());
            assertEquals(1, status.getDocumentOperationRetentionYears());
            assertEquals(50L, status.getSystemConfigCount());
            assertEquals(6, status.getSystemConfigRetentionMonths());
            assertEquals(200L, status.getPermissionAuditCount());
            assertEquals(25L, status.getUserManagementCount());
            assertNotNull(status.getLastCleanupTime());
        }

        @Test
        @DisplayName("返回零计数当无日志")
        void shouldReturnZeroCountsWhenNoLogs() {
            when(auditLogMapper.countByCategory("DOCUMENT_OPERATION")).thenReturn(0L);
            when(auditLogMapper.countByCategory("SYSTEM_CONFIG")).thenReturn(0L);
            when(auditLogMapper.countByCategory("USER_MANAGEMENT")).thenReturn(0L);
            when(permissionAuditLogMapper.countAll()).thenReturn(0L);

            AuditLogRetentionService.RetentionStatus status = auditLogRetentionService.getRetentionStatus();

            assertNotNull(status);
            assertEquals(0L, status.getDocumentOperationCount());
            assertEquals(0L, status.getSystemConfigCount());
            assertEquals(0L, status.getPermissionAuditCount());
            assertEquals(0L, status.getUserManagementCount());
        }
    }

    @Nested
    @DisplayName("cleanupExpiredAuditLogs - 清理过期日志")
    class CleanupExpiredAuditLogsTests {

        @Test
        @DisplayName("执行清理不抛异常")
        void shouldExecuteCleanupWithoutException() {
            when(auditLogMapper.deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any())).thenReturn(0);
            when(auditLogMapper.deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any())).thenReturn(0);

            assertDoesNotThrow(() -> auditLogRetentionService.cleanupExpiredAuditLogs());

            verify(auditLogMapper).deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any());
            verify(auditLogMapper).deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any());
        }

        @Test
        @DisplayName("清理时删除过期文档操作日志")
        void shouldDeleteExpiredDocumentOperationLogs() {
            when(auditLogMapper.deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any())).thenReturn(10);
            when(auditLogMapper.deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any())).thenReturn(0);

            auditLogRetentionService.cleanupExpiredAuditLogs();

            verify(auditLogMapper).deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any());
        }

        @Test
        @DisplayName("清理时删除过期系统配置日志")
        void shouldDeleteExpiredSystemConfigLogs() {
            when(auditLogMapper.deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any())).thenReturn(0);
            when(auditLogMapper.deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any())).thenReturn(5);

            auditLogRetentionService.cleanupExpiredAuditLogs();

            verify(auditLogMapper).deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any());
        }

        @Test
        @DisplayName("同时清理文档操作和系统配置日志")
        void shouldCleanupBothCategories() {
            when(auditLogMapper.deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any())).thenReturn(10);
            when(auditLogMapper.deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any())).thenReturn(5);

            auditLogRetentionService.cleanupExpiredAuditLogs();

            verify(auditLogMapper).deleteExpiredByCategory(eq("DOCUMENT_OPERATION"), any());
            verify(auditLogMapper).deleteExpiredByCategory(eq("SYSTEM_CONFIG"), any());
        }
    }

    @Nested
    @DisplayName("RetentionStatus record 测试")
    class RetentionStatusTests {

        @Test
        @DisplayName("builder创建完整的RetentionStatus")
        void shouldBuildRetentionStatus() {
            AuditLogRetentionService.RetentionStatus status = AuditLogRetentionService.RetentionStatus.builder()
                    .documentOperationCount(100L)
                    .documentOperationRetentionYears(1)
                    .systemConfigCount(50L)
                    .systemConfigRetentionMonths(6)
                    .permissionAuditCount(200L)
                    .userManagementCount(25L)
                    .lastCleanupTime(java.time.LocalDateTime.now())
                    .build();

            assertEquals(100L, status.getDocumentOperationCount());
            assertEquals(1, status.getDocumentOperationRetentionYears());
            assertEquals(50L, status.getSystemConfigCount());
            assertEquals(6, status.getSystemConfigRetentionMonths());
            assertEquals(200L, status.getPermissionAuditCount());
            assertEquals(25L, status.getUserManagementCount());
            assertNotNull(status.getLastCleanupTime());
        }

        @Test
        @DisplayName("setter方法正常工作")
        void shouldAllowSetters() {
            AuditLogRetentionService.RetentionStatus status = AuditLogRetentionService.RetentionStatus.builder().build();
            status.setDocumentOperationCount(999L);
            status.setSystemConfigRetentionMonths(12);

            assertEquals(999L, status.getDocumentOperationCount());
            assertEquals(12, status.getSystemConfigRetentionMonths());
        }
    }
}
