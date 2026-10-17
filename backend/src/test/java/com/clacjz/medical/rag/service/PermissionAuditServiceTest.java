package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.PermissionAuditLogDTO;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.PermissionAuditLog;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.PermissionAuditLogMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionAuditService 单元测试")
class PermissionAuditServiceTest {

    @Mock
    private PermissionAuditLogMapper auditLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private PermissionAuditService permissionAuditService;

    private User testUser;
    private Document testDocument;
    private PermissionAuditLog testAuditLog;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .username("testuser")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        testDocument = Document.builder()
                .id("doc-456")
                .title("Test Document")
                .build();

        testAuditLog = PermissionAuditLog.builder()
                .id(UUID.randomUUID().toString())
                .operatorId("user-123")
                .operationType(AuditOperationTypeEnums.GRANT)
                .targetType(AuditTargetTypeEnums.DOCUMENT)
                .targetId("doc-456")
                .ipAddress("127.0.0.1")
                .userAgent("TestAgent")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("记录权限变更测试")
    class LogPermissionChangeTests {

        @Test
        @DisplayName("记录权限变更成功")
        void logPermissionChange_Success() {
            Map<String, Object> oldValue = Map.of("level", "READ");
            Map<String, Object> newValue = Map.of("level", "WRITE");

            permissionAuditService.logPermissionChange(
                    "operator-123",
                    AuditOperationTypeEnums.GRANT,
                    AuditTargetTypeEnums.DOCUMENT,
                    "doc-123",
                    oldValue,
                    newValue,
                    "192.168.1.1",
                    "Mozilla/5.0"
            );

            ArgumentCaptor<PermissionAuditLog> captor = ArgumentCaptor.forClass(PermissionAuditLog.class);
            verify(auditLogMapper).insert(captor.capture());

            PermissionAuditLog savedLog = captor.getValue();
            assertEquals("operator-123", savedLog.getOperatorId());
            assertEquals(AuditOperationTypeEnums.GRANT, savedLog.getOperationType());
            assertEquals(AuditTargetTypeEnums.DOCUMENT, savedLog.getTargetType());
            assertEquals("doc-123", savedLog.getTargetId());
            assertEquals("192.168.1.1", savedLog.getIpAddress());
        }
    }

    @Nested
    @DisplayName("获取审计日志测试")
    class GetAuditLogsTests {

        @Test
        @DisplayName("获取审计日志成功")
        void getAuditLogs_Success() {
            List<PermissionAuditLog> logList = List.of(testAuditLog);
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();

            when(auditLogMapper.selectByFilters(
                    isNull(), isNull(), isNull(), isNull(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(logList);
            when(auditLogMapper.countByFilters(
                    isNull(), isNull(), isNull(), isNull(), any(), any()))
                    .thenReturn(1L);
            when(userMapper.selectById("user-123")).thenReturn(testUser);
            when(documentMapper.selectById("doc-456")).thenReturn(testDocument);

            var result = permissionAuditService.getAuditLogs(
                    0, 20, null, null, null, null, startTime, endTime);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取审计日志返回空结果")
        void getAuditLogs_EmptyResult() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();

            when(auditLogMapper.selectByFilters(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(auditLogMapper.countByFilters(
                    any(), any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            var result = permissionAuditService.getAuditLogs(
                    0, 20, null, null, null, null, startTime, endTime);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取最近审计日志测试")
    class GetRecentAuditLogsTests {

        @Test
        @DisplayName("获取最近审计日志成功")
        void getRecentAuditLogs_Success() {
            List<PermissionAuditLog> logs = List.of(testAuditLog);

            when(auditLogMapper.selectRecentByTarget(
                    eq(AuditTargetTypeEnums.DOCUMENT.name()), eq("doc-456"), eq(10)))
                    .thenReturn(logs);
            when(userMapper.selectById("user-123")).thenReturn(testUser);
            when(documentMapper.selectById("doc-456")).thenReturn(testDocument);

            List<PermissionAuditLogDTO> result = permissionAuditService.getRecentAuditLogs(
                    "DOCUMENT", "doc-456", 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("获取最近审计日志返回空")
        void getRecentAuditLogs_EmptyResult() {
            when(auditLogMapper.selectRecentByTarget(
                    any(), any(), anyInt()))
                    .thenReturn(List.of());

            List<PermissionAuditLogDTO> result = permissionAuditService.getRecentAuditLogs(
                    "DOCUMENT", "doc-456", 10);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("目标名称获取测试")
    class TargetNameTests {

        @Test
        @DisplayName("获取用户目标名称")
        void getTargetName_User() {
            PermissionAuditLog userLog = PermissionAuditLog.builder()
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .operatorId("user-123")
                    .targetType(AuditTargetTypeEnums.USER)
                    .targetId("user-123")
                    .build();

            when(userMapper.selectById("user-123")).thenReturn(testUser);

            List<PermissionAuditLog> logList = List.of(userLog);
            when(auditLogMapper.selectByFilters(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(logList);
            when(auditLogMapper.countByFilters(
                    any(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            permissionAuditService.getAuditLogs(0, 20, null, null, null, null,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now());

            verify(userMapper).selectById("user-123");
        }

        @Test
        @DisplayName("获取文档目标名称")
        void getTargetName_Document() {
            PermissionAuditLog docLog = PermissionAuditLog.builder()
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .targetType(AuditTargetTypeEnums.DOCUMENT)
                    .targetId("doc-456")
                    .build();

            List<PermissionAuditLog> logList = List.of(docLog);
            when(auditLogMapper.selectByFilters(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(logList);
            when(auditLogMapper.countByFilters(
                    any(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);
            when(userMapper.selectById(any())).thenReturn(null);
            when(documentMapper.selectById("doc-456")).thenReturn(testDocument);

            permissionAuditService.getAuditLogs(0, 20, null, null, null, null,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now());

            verify(documentMapper).selectById("doc-456");
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("零限制获取最近日志")
        void getRecentAuditLogs_ZeroLimit() {
            when(auditLogMapper.selectRecentByTarget(
                    any(), any(), anyInt()))
                    .thenReturn(List.of());

            List<PermissionAuditLogDTO> result = permissionAuditService.getRecentAuditLogs(
                    "DOCUMENT", "doc-456", 1);

            assertNotNull(result);
        }

        @Test
        @DisplayName("操作人不存在")
        void getAuditLogs_OperatorNotFound() {
            PermissionAuditLog log = PermissionAuditLog.builder()
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .targetType(AuditTargetTypeEnums.DOCUMENT)
                    .targetId("doc-456")
                    .build();

            when(auditLogMapper.selectRecentByTarget(
                    any(), any(), anyInt()))
                    .thenReturn(List.of(log));
            when(userMapper.selectById(any())).thenReturn(null);
            when(documentMapper.selectById("doc-456")).thenReturn(testDocument);

            List<PermissionAuditLogDTO> result = permissionAuditService.getRecentAuditLogs(
                    "DOCUMENT", "doc-456", 10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get(0).getOperator());
        }

        @Test
        @DisplayName("目标不存在")
        void getAuditLogs_TargetNotFound() {
            PermissionAuditLog log = PermissionAuditLog.builder()
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .operatorId("user-123")
                    .targetType(AuditTargetTypeEnums.USER)
                    .targetId("nonexistent")
                    .build();

            when(auditLogMapper.selectRecentByTarget(
                    any(), any(), anyInt()))
                    .thenReturn(List.of(log));
            when(userMapper.selectById("user-123")).thenReturn(testUser);
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            List<PermissionAuditLogDTO> result = permissionAuditService.getRecentAuditLogs(
                    "USER", "nonexistent", 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }
}
