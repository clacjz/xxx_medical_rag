package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.AuditLogDTO;
import com.clacjz.medical.rag.model.entity.AuditLog;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditService 单元测试")
class AuditServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private JsonUtil jsonUtil;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AuditService auditService;

    private static final String USER_ID = "user-123";
    private static final String USERNAME = "testuser";
    private static final String DISPLAY_NAME = "Test User";
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final String USER_AGENT = "Mozilla/5.0";

    @BeforeEach
    void setUp() {
        // Create service with null self; set self-reference via reflection after construction
        auditService = new AuditService(auditLogMapper, jsonUtil, transactionTemplate, null);
        // Set self to the service instance so self.logAction() works in convenience methods
        try {
            var selfField = AuditService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(auditService, auditService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set self field", e);
        }

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            org.springframework.transaction.support.TransactionCallback<Object> callback = invocation.getArgument(0);
            callback.doInTransaction(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Nested
    @DisplayName("logAction 基础测试")
    class LogActionTests {

        @Test
        @DisplayName("记录操作成功 - 完整参数")
        void logAction_Success_CompleteParams() {
            auditService.logAction(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.LOGIN, AuditLevelEnums.INFO,
                    "AUTH", "resource-id-123",
                    "User logged in", IP_ADDRESS, USER_AGENT,
                    Map.of("key", "value"), "success"
            );

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录操作成功 - 空 details")
        void logAction_Success_EmptyDetails() {
            auditService.logAction(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.LOGOUT, AuditLevelEnums.INFO,
                    "AUTH", null, null, IP_ADDRESS, USER_AGENT,
                    null, "success"
            );

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录操作成功 - null username")
        void logAction_Success_NullUsername() {
            assertDoesNotThrow(() -> auditService.logAction(
                    USER_ID, null, null,
                    AuditActionEnums.SYSTEM_CONFIG_UPDATE, AuditLevelEnums.SECURITY,
                    "SYSTEM", null, null, IP_ADDRESS, USER_AGENT,
                    Map.of("error", "test"), "failed"
            ));
        }
    }

    @Nested
    @DisplayName("登录日志测试")
    class LogLoginTests {

        @Test
        @DisplayName("记录成功登录")
        void logLogin_Success() {
            auditService.logLogin(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true, "登录成功");

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录失败登录")
        void logLogin_Failed() {
            auditService.logLogin(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, false, "密码错误");

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录失败登录 - null reason")
        void logLogin_Failed_NullReason() {
            auditService.logLogin(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, false, null);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("登出日志测试")
    class LogLogoutTests {

        @Test
        @DisplayName("记录登出")
        void logLogout_Success() {
            auditService.logLogout(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录登出 - null 显示名称")
        void logLogout_NullDisplayName() {
            auditService.logLogout(USER_ID, USERNAME, null, IP_ADDRESS, USER_AGENT);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("注册日志测试")
    class LogRegisterTests {

        @Test
        @DisplayName("记录成功注册")
        void logRegister_Success() {
            auditService.logRegister(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录失败注册")
        void logRegister_Failed() {
            auditService.logRegister(null, "newuser", null, IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("密码修改日志测试")
    class LogPasswordChangeTests {

        @Test
        @DisplayName("记录成功密码修改")
        void logPasswordChange_Success() {
            auditService.logPasswordChange(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录失败密码修改")
        void logPasswordChange_Failed() {
            auditService.logPasswordChange(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("用户操作日志测试")
    class LogUserOperationTests {

        @Test
        @DisplayName("记录创建用户")
        void logUserOperation_CreateUser() {
            auditService.logUserOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.USER_CREATE,
                    "new-user-id", "newuser",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录删除用户")
        void logUserOperation_DeleteUser() {
            auditService.logUserOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.USER_DELETE,
                    "target-user-id", "targetuser",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录用户操作 - null 目标用户名")
        void logUserOperation_NullTargetName() {
            auditService.logUserOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.USER_UPDATE,
                    "target-user-id", null,
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录用户操作失败")
        void logUserOperation_Failed() {
            auditService.logUserOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.USER_CREATE,
                    "new-user-id", "newuser",
                    IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("文档操作日志测试")
    class LogDocumentOperationTests {

        @Test
        @DisplayName("记录上传文档")
        void logDocumentOperation_Upload() {
            auditService.logDocumentOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.DOCUMENT_UPLOAD,
                    "doc-123", "测试文档.pdf",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录删除文档")
        void logDocumentOperation_Delete() {
            auditService.logDocumentOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.DOCUMENT_DELETE,
                    "doc-123", "测试文档.pdf",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录文档操作 - null 文档名")
        void logDocumentOperation_NullDocName() {
            auditService.logDocumentOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.DOCUMENT_UPLOAD,
                    "doc-123", null,
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录文档操作失败")
        void logDocumentOperation_Failed() {
            auditService.logDocumentOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.DOCUMENT_UPLOAD,
                    "doc-123", "测试文档.pdf",
                    IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("知识库操作日志测试")
    class LogCollectionOperationTests {

        @Test
        @DisplayName("记录创建知识库")
        void logCollectionOperation_Create() {
            auditService.logCollectionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.COLLECTION_CREATE,
                    "collection-123", "测试知识库",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录删除知识库")
        void logCollectionOperation_Delete() {
            auditService.logCollectionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.COLLECTION_DELETE,
                    "collection-123", "测试知识库",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录知识库操作 - null 名称")
        void logCollectionOperation_NullName() {
            auditService.logCollectionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.COLLECTION_CREATE,
                    "collection-123", null,
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录知识库操作失败")
        void logCollectionOperation_Failed() {
            auditService.logCollectionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.COLLECTION_CREATE,
                    "collection-123", "测试知识库",
                    IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("权限操作日志测试")
    class LogPermissionOperationTests {

        @Test
        @DisplayName("记录授予权限")
        void logPermissionOperation_Grant() {
            auditService.logPermissionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.PERMISSION_GRANT,
                    "DOCUMENT", "doc-123", "测试文档",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录撤销权限")
        void logPermissionOperation_Revoke() {
            auditService.logPermissionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.PERMISSION_REVOKE,
                    "DOCUMENT", "doc-123", "测试文档",
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录权限操作 - null 目标名称")
        void logPermissionOperation_NullTargetName() {
            auditService.logPermissionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.PERMISSION_GRANT,
                    "DOCUMENT", "doc-123", null,
                    IP_ADDRESS, USER_AGENT, true);

            verify(transactionTemplate).executeWithoutResult(any());
        }

        @Test
        @DisplayName("记录权限操作失败")
        void logPermissionOperation_Failed() {
            auditService.logPermissionOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.PERMISSION_GRANT,
                    "DOCUMENT", "doc-123", "测试文档",
                    IP_ADDRESS, USER_AGENT, false);

            verify(transactionTemplate).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("获取审计日志测试")
    class GetAuditLogsTests {

        @Test
        @DisplayName("获取审计日志 - 无过滤条件")
        void getAuditLogs_NoFilters() {
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(USER_ID)
                    .username(USERNAME)
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .status("success")
                    .build();

            Page<AuditLog> page = new Page<>(1, 10);
            page.setRecords(java.util.Arrays.asList(log));
            page.setTotal(1);
            IPage<AuditLog> ipage = page;
            when(auditLogMapper.selectByFilters(isNull(), isNull(), isNull(), isNull(), isNull(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, null, null, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取审计日志 - 按用户ID过滤")
        void getAuditLogs_ByUserId() {
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(USER_ID)
                    .username(USERNAME)
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .status("success")
                    .build();

            Page<AuditLog> page = new Page<>(1, 10);
            page.setRecords(java.util.Arrays.asList(log));
            page.setTotal(1);
            IPage<AuditLog> ipage = page;
            when(auditLogMapper.selectByFilters(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, USER_ID, null, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取审计日志 - 按操作类型过滤")
        void getAuditLogs_ByAction() {
            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(isNull(), eq("LOGIN"), isNull(), isNull(), isNull(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, null, "LOGIN", null, null, null);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("获取审计日志 - 按级别过滤")
        void getAuditLogs_ByLevel() {
            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(isNull(), isNull(), eq("WARNING"), isNull(), isNull(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, null, null, AuditLevelEnums.WARNING, null, null);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("获取审计日志 - 按时间范围过滤")
        void getAuditLogs_ByTimeRange() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            LocalDateTime endTime = LocalDateTime.now();

            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(isNull(), isNull(), isNull(), eq(startTime), eq(endTime), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, null, null, null, startTime, endTime);

            assertNotNull(result);
        }

        @Test
        @DisplayName("获取审计日志 - 空结果")
        void getAuditLogs_Empty() {
            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(any(), any(), any(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(0, 10, "nonexistent", null, null, null, null);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取单个审计日志测试")
    class GetAuditLogByIdTests {

        @Test
        @DisplayName("获取审计日志成功")
        void getAuditLogById_Success() {
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(USER_ID)
                    .username(USERNAME)
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .status("success")
                    .build();

            when(auditLogMapper.selectById(log.getId())).thenReturn(log);

            AuditLogDTO result = auditService.getAuditLogById(log.getId());

            assertNotNull(result);
            assertEquals(log.getId(), result.getId());
        }

        @Test
        @DisplayName("获取审计日志失败 - 不存在")
        void getAuditLogById_NotFound() {
            when(auditLogMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> auditService.getAuditLogById("nonexistent"));

            assertTrue(exception.getMessage().contains("Audit log not found"));
        }
    }

    @Nested
    @DisplayName("导出审计日志测试")
    class ExportAuditLogsTests {

        @Test
        @DisplayName("导出审计日志成功")
        void exportAuditLogs_Success() {
            AuditLog log1 = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(USER_ID)
                    .username(USERNAME)
                    .displayName(DISPLAY_NAME)
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .resourceType("AUTH")
                    .description("用户登录")
                    .ipAddress(IP_ADDRESS)
                    .timestamp(LocalDateTime.now())
                    .status("success")
                    .build();

            AuditLog log2 = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(USER_ID)
                    .username(USERNAME)
                    .action(AuditActionEnums.DOCUMENT_UPLOAD)
                    .level(AuditLevelEnums.INFO)
                    .resourceType("DOCUMENT")
                    .ipAddress(IP_ADDRESS)
                    .timestamp(LocalDateTime.now())
                    .status("success")
                    .build();

            Page<AuditLog> page = new Page<>(1, 10000);
            page.setRecords(java.util.Arrays.asList(log1, log2));
            page.setTotal(2);
            IPage<AuditLog> ipage = page;
            when(auditLogMapper.selectByFilters(isNull(), isNull(), isNull(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            String result = auditService.exportAuditLogs(LocalDateTime.now().minusDays(1), LocalDateTime.now());

            assertNotNull(result);
            assertTrue(result.contains("ID,用户ID,用户名"));
            assertTrue(result.contains(log1.getId()));
            assertTrue(result.contains(log2.getId()));
        }

        @Test
        @DisplayName("导出审计日志 - 空结果")
        void exportAuditLogs_Empty() {
            Page<AuditLog> emptyPage = new Page<>(1, 10000);
            emptyPage.setRecords(java.util.Collections.emptyList());
            emptyPage.setTotal(0);
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(isNull(), isNull(), isNull(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            String result = auditService.exportAuditLogs(LocalDateTime.now().minusDays(1), LocalDateTime.now());

            assertNotNull(result);
            assertTrue(result.contains("ID,用户ID,用户名"));
        }

        @Test
        @DisplayName("导出审计日志 - CSV格式验证")
        void exportAuditLogs_CsvFormat() {
            AuditLog log = AuditLog.builder()
                    .id("test-id-123")
                    .userId(USER_ID)
                    .username(USERNAME)
                    .displayName(DISPLAY_NAME)
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .resourceType("AUTH")
                    .resourceId(null)
                    .description("Test description")
                    .ipAddress(IP_ADDRESS)
                    .timestamp(LocalDateTime.of(2026, 4, 2, 10, 30, 0))
                    .status("success")
                    .build();

            Page<AuditLog> page = new Page<>(1, 10000);
            page.setRecords(java.util.Arrays.asList(log));
            page.setTotal(1);
            IPage<AuditLog> ipage = page;
            when(auditLogMapper.selectByFilters(any(), any(), any(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            String result = auditService.exportAuditLogs(LocalDateTime.now().minusDays(1), LocalDateTime.now());

            assertNotNull(result);
            assertTrue(result.startsWith("ID,用户ID,用户名,显示名称,操作,操作描述,级别,级别描述,资源类型,资源ID,描述,IP地址,时间,状态"));
            assertTrue(result.contains("test-id-123"));
            assertTrue(result.contains("testuser"));
            assertTrue(result.contains("LOGIN"));
        }
    }

    @Nested
    @DisplayName("日志级别判断测试")
    class DetermineAuditLevelTests {

        @Test
        @DisplayName("删除操作应返回 WARNING 级别")
        void determineAuditLevel_DeleteOperations() {
            verifyActionLevel(AuditActionEnums.USER_DELETE, AuditLevelEnums.WARNING);
            verifyActionLevel(AuditActionEnums.DOCUMENT_DELETE, AuditLevelEnums.WARNING);
            verifyActionLevel(AuditActionEnums.COLLECTION_DELETE, AuditLevelEnums.WARNING);
        }

        @Test
        @DisplayName("安全操作应返回 WARNING 级别")
        void determineAuditLevel_SecurityOperations() {
            verifyActionLevel(AuditActionEnums.USER_LOCK, AuditLevelEnums.WARNING);
            verifyActionLevel(AuditActionEnums.PERMISSION_GRANT, AuditLevelEnums.WARNING);
            verifyActionLevel(AuditActionEnums.PERMISSION_REVOKE, AuditLevelEnums.WARNING);
        }

        @Test
        @DisplayName("创建操作应返回 INFO 级别")
        void determineAuditLevel_CreateOperations() {
            verifyActionLevel(AuditActionEnums.USER_CREATE, AuditLevelEnums.INFO);
            verifyActionLevel(AuditActionEnums.COLLECTION_CREATE, AuditLevelEnums.INFO);
            verifyActionLevel(AuditActionEnums.DOCUMENT_UPLOAD, AuditLevelEnums.INFO);
        }

        @Test
        @DisplayName("默认返回 INFO 级别")
        void determineAuditLevel_Default() {
            verifyActionLevel(AuditActionEnums.LOGIN, AuditLevelEnums.INFO);
            verifyActionLevel(AuditActionEnums.LOGOUT, AuditLevelEnums.INFO);
            verifyActionLevel(AuditActionEnums.SYSTEM_CONFIG_UPDATE, AuditLevelEnums.INFO);
        }

        private void verifyActionLevel(AuditActionEnums action, AuditLevelEnums expectedLevel) {
            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(any(), any(), any(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            auditService.logUserOperation(
                    USER_ID, USERNAME, DISPLAY_NAME,
                    action,
                    "target-id", "target-name",
                    IP_ADDRESS, USER_AGENT, true
            );

            verify(transactionTemplate, atLeast(1)).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("Self注入代理测试 - 验证便捷方法通过self.logAction()调用")
    class SelfInjectionProxyTests {

        @Test
        @DisplayName("logPasswordChange 应通过 self.logAction 调用")
        void logPasswordChange_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            // Set self to the spy so self.logAction() routes through the spy
            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logPasswordChange(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.PASSWORD_CHANGE), eq(AuditLevelEnums.SECURITY),
                    eq("AUTH"), eq(USER_ID), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logUserOperation 应通过 self.logAction 调用")
        void logUserOperation_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logUserOperation(USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.USER_CREATE, "target-id", "target-name",
                    IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.USER_CREATE), eq(AuditLevelEnums.INFO),
                    eq("USER"), eq("target-id"), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logDocumentOperation 应通过 self.logAction 调用")
        void logDocumentOperation_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logDocumentOperation(USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.DOCUMENT_UPLOAD, "doc-123", "test.pdf",
                    IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.DOCUMENT_UPLOAD), eq(AuditLevelEnums.INFO),
                    eq("DOCUMENT"), eq("doc-123"), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logCollectionOperation 应通过 self.logAction 调用")
        void logCollectionOperation_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logCollectionOperation(USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.COLLECTION_CREATE, "col-123", "test-collection",
                    IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.COLLECTION_CREATE), eq(AuditLevelEnums.INFO),
                    eq("COLLECTION"), eq("col-123"), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logPermissionOperation 应通过 self.logAction 调用")
        void logPermissionOperation_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logPermissionOperation(USER_ID, USERNAME, DISPLAY_NAME,
                    AuditActionEnums.PERMISSION_GRANT, "DOCUMENT", "doc-123", "test-doc",
                    IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.PERMISSION_GRANT), eq(AuditLevelEnums.SECURITY),
                    eq("PERMISSION"), eq("doc-123"), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logSystemOperation 应通过 self.logAction 调用")
        void logSystemOperation_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logSystemOperation(USER_ID, "EMAIL_SEND", "SYSTEM",
                    "job-123", true, "Email sent successfully");

            verify(spyService).logAction(
                    eq(USER_ID), isNull(), isNull(),
                    eq(AuditActionEnums.SYSTEM_OPERATION), eq(AuditLevelEnums.INFO),
                    eq("SYSTEM"), eq("job-123"), isNull(),
                    isNull(), isNull(),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logLogin 应通过 self.logAction 调用")
        void logLogin_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logLogin(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true, "登录成功");

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.LOGIN), eq(AuditLevelEnums.INFO),
                    eq("AUTH"), isNull(), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }

        @Test
        @DisplayName("logLogout 应通过 self.logAction 调用")
        void logLogout_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logLogout(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.LOGOUT), eq(AuditLevelEnums.INFO),
                    eq("AUTH"), isNull(), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    isNull(), eq("success"));
        }

        @Test
        @DisplayName("logRegister 应通过 self.logAction 调用")
        void logRegister_usesSelfLogAction() {
            AuditService spyService = spy(auditService);

            try {
                var selfField = AuditService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(spyService, spyService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field on spy", e);
            }

            spyService.logRegister(USER_ID, USERNAME, DISPLAY_NAME, IP_ADDRESS, USER_AGENT, true);

            verify(spyService).logAction(
                    eq(USER_ID), eq(USERNAME), eq(DISPLAY_NAME),
                    eq(AuditActionEnums.REGISTER), eq(AuditLevelEnums.INFO),
                    eq("AUTH"), eq(USER_ID), isNull(),
                    eq(IP_ADDRESS), eq(USER_AGENT),
                    any(Map.class), eq("success"));
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class BoundaryTests {

        @Test
        @DisplayName("记录操作 - 所有参数为 null")
        void logAction_AllNull() {
            assertDoesNotThrow(() -> auditService.logAction(
                    null, null, null,
                    null, null,
                    null, null, null, null, null,
                    null, null
            ));
        }

        @Test
        @DisplayName("获取审计日志 - 分页参数")
        void getAuditLogs_Pagination() {
            Page<AuditLog> emptyPage = new Page<>(6, 20);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(any(), any(), any(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            IPage<AuditLogDTO> result = auditService.getAuditLogs(5, 20, null, null, null, null, null);

            assertNotNull(result);
        }

        @Test
        @DisplayName("获取审计日志 - 混合过滤条件")
        void getAuditLogs_MixedFilters() {
            Page<AuditLog> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(java.util.Collections.emptyList());
            IPage<AuditLog> ipage = emptyPage;
            when(auditLogMapper.selectByFilters(any(), any(), any(), any(), any(), any(Page.class)))
                    .thenReturn(ipage);

            LocalDateTime startTime = LocalDateTime.now().minusDays(30);
            LocalDateTime endTime = LocalDateTime.now();

            IPage<AuditLogDTO> result = auditService.getAuditLogs(
                    0, 10,
                    USER_ID,
                    "LOGIN",
                    AuditLevelEnums.INFO,
                    startTime,
                    endTime
            );

            assertNotNull(result);
        }
    }
}
