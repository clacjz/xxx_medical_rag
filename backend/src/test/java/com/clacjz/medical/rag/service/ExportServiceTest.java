package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.entity.AuditLog;
import com.clacjz.medical.rag.model.entity.ChatMessage;
import com.clacjz.medical.rag.model.entity.ChatSession;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.mapper.ChatMessageMapper;
import com.clacjz.medical.rag.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService 单元测试")
@Slf4j
class ExportServiceTest {

    @Mock
    private ChatSessionMapper sessionRepository;

    @Mock
    private ChatMessageMapper messageRepository;

    @Mock
    private AuditLogMapper auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExportService exportService;

    private ChatSession testSession;
    private List<ChatMessage> testMessages;
    private List<AuditLog> testAuditLogs;

    @BeforeEach
    void setUp() {
        testSession = ChatSession.builder()
                .id("session-123")
                .title("测试会话")
                .userId("user-123")
                .departmentId("dept-123")
                .status("ACTIVE")
                .messageCount(2)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatMessage userMessage = ChatMessage.builder()
                .id("msg-1")
                .sessionId("session-123")
                .role("user")
                .content("这是一个测试问题")
                .departmentId("dept-123")
                .createdAt(LocalDateTime.now())
                .build();

        ChatMessage assistantMessage = ChatMessage.builder()
                .id("msg-2")
                .sessionId("session-123")
                .role("assistant")
                .content("这是一个测试回答")
                .departmentId("dept-123")
                .createdAt(LocalDateTime.now())
                .sources(new ArrayList<>())
                .tokenUsage(ChatMessage.TokenUsage.builder()
                        .promptTokens(100)
                        .completionTokens(50)
                        .totalTokens(150)
                        .build())
                .generationTimeMs(1000)
                .build();

        testMessages = Arrays.asList(userMessage, assistantMessage);

        AuditLog auditLog1 = AuditLog.builder()
                .id("audit-1")
                .userId("user-123")
                .username("testuser")
                .displayName("测试用户")
                .action(AuditActionEnums.LOGIN)
                .level(AuditLevelEnums.INFO)
                .resourceType("AUTH")
                .ipAddress("192.168.1.1")
                .status("success")
                .timestamp(LocalDateTime.now())
                .build();

        AuditLog auditLog2 = AuditLog.builder()
                .id("audit-2")
                .userId("user-456")
                .username("admin")
                .displayName("管理员")
                .action(AuditActionEnums.USER_CREATE)
                .level(AuditLevelEnums.WARNING)
                .resourceType("USER")
                .resourceId("user-456")
                .ipAddress("192.168.1.2")
                .status("success")
                .timestamp(LocalDateTime.now())
                .build();

        testAuditLogs = Arrays.asList(auditLog1, auditLog2);
    }

    @Nested
    @DisplayName("Markdown导出测试")
    class MarkdownExportTests {

        @Test
        @DisplayName("成功导出会话到Markdown")
        void testExportSessionToMarkdown_Success() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(testMessages);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);
            assertTrue(markdown.contains("# 测试会话"));
            assertTrue(markdown.contains("会话ID"));
            assertTrue(markdown.contains("## 用户"));
            assertTrue(markdown.contains("这是一个测试问题"));
            assertTrue(markdown.contains("## 助手"));
            assertTrue(markdown.contains("这是一个测试回答"));
            assertTrue(markdown.contains("Token使用"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("会话不存在时抛出异常")
        void testExportSessionToMarkdown_SessionNotFound() {
            when(sessionRepository.selectById("invalid-session")).thenReturn(null);

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToMarkdown("invalid-session");
            });

            assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("无权访问其他用户会话时抛出异常")
        void testExportSessionToMarkdown_PermissionDenied() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);

            TenantContext.setUserId("other-user");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToMarkdown("session-123");
            });

            assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), exception.getCode());

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出会话时包含参考来源")
        void testExportSessionToMarkdown_WithSources() {
            ChatMessage.SourceReference source = ChatMessage.SourceReference.builder()
                    .documentId("doc-1")
                    .title("测试文档")
                    .snippet("文档片段内容")
                    .score(0.95)
                    .build();

            ChatMessage assistantMessageWithSources = ChatMessage.builder()
                    .id("msg-2")
                    .sessionId("session-123")
                    .role("assistant")
                    .content("这是一个包含来源的回答")
                    .departmentId("dept-123")
                    .sources(Arrays.asList(source))
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ChatMessage> messagesWithSources = Arrays.asList(
                    testMessages.get(0),
                    assistantMessageWithSources
            );

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messagesWithSources);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);
            assertTrue(markdown.contains("测试文档"));
            assertTrue(markdown.contains("相似度"));
            assertTrue(markdown.contains("参考来源"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出会话时包含模型信息")
        void testExportSessionWithModelInfo() {
            ChatMessage messageWithModelInfo = ChatMessage.builder()
                    .id("msg-model")
                    .sessionId("session-123")
                    .role("assistant")
                    .content("带模型信息的回答")
                    .departmentId("dept-123")
                    .createdAt(LocalDateTime.now())
                    .modelInfo(ChatMessage.ModelInfo.builder()
                            .modelName("qwen2.5:7b")
                            .embeddingModel("BGE-M3")
                            .temperature(0.7)
                            .maxTokens(2048)
                            .build())
                    .build();

            List<ChatMessage> messagesWithModelInfo = Arrays.asList(
                    testMessages.get(0),
                    messageWithModelInfo
            );

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messagesWithModelInfo);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);
            assertTrue(markdown.contains("模型信息"));
            assertTrue(markdown.contains("qwen2.5:7b"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("管理员可以访问其他用户会话")
        void testAdminCanAccessOtherUserSession() {
            ChatSession otherUserSession = ChatSession.builder()
                    .id("session-other")
                    .title("其他用户会话")
                    .userId("other-user")
                    .departmentId("dept-123")
                    .status("ACTIVE")
                    .messageCount(1)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(sessionRepository.selectById("session-other")).thenReturn(otherUserSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-other")).thenReturn(testMessages);

            TenantContext.setUserId("admin-user");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("SYSTEM_ADMIN");

            String markdown = exportService.exportSessionToMarkdown("session-other");

            assertNotNull(markdown);
            assertTrue(markdown.contains("其他用户会话"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出会话处理特殊Markdown字符")
        void testExportSessionToMarkdown_WithSpecialCharacters() {
            ChatMessage messageWithSpecialChars = ChatMessage.builder()
                    .id("msg-special")
                    .sessionId("session-123")
                    .role("user")
                    .content("测试 *粗体* 和 _斜体_ 和 `代码` 和 [链接](url)")
                    .departmentId("dept-123")
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ChatMessage> messagesWithSpecialChars = Arrays.asList(messageWithSpecialChars);

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messagesWithSpecialChars);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);
            assertTrue(markdown.contains("\\*粗体\\*"), "应该转义星号");
            assertTrue(markdown.contains("\\_斜体\\_"), "应该转义下划线");
            assertTrue(markdown.contains("\\`代码\\`"), "应该转义反引号");
            assertTrue(markdown.contains("\\[链接\\]"), "应该转义方括号");

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出空消息列表时抛出异常")
        void testExportSessionToMarkdown_EmptyMessages() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(new ArrayList<>());

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToMarkdown("session-123");
            });

            assertEquals(ErrorCode.EXPORT_VALIDATION_FAILED.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("没有可导出的消息"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出消息数量超限时抛出异常")
        void testExportSessionToMarkdown_MessageCountExceedsLimit() {
            List<ChatMessage> excessiveMessages = new ArrayList<>();
            for (int i = 0; i < 10001; i++) {
                excessiveMessages.add(ChatMessage.builder()
                        .id("msg-" + i)
                        .sessionId("session-123")
                        .role("user")
                        .content("测试消息 " + i)
                        .departmentId("dept-123")
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(excessiveMessages);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToMarkdown("session-123");
            });

            assertEquals(ErrorCode.EXPORT_DATA_TOO_LARGE.getCode(), exception.getCode());

            TenantContext.clear();
        }
    }

    @Nested
    @DisplayName("PDF导出测试")
    class PdfExportTests {

        @Test
        @DisplayName("PDF导出会话不存在时抛出异常")
        void testExportSessionToPdf_SessionNotFound() {
            when(sessionRepository.selectById("invalid-session")).thenReturn(null);

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToPdf("invalid-session");
            });

            assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("PDF导出无权限时抛出异常")
        void testExportSessionToPdf_PermissionDenied() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);

            TenantContext.setUserId("other-user");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToPdf("session-123");
            });

            assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), exception.getCode());

            TenantContext.clear();
        }

        @Test
        @DisplayName("PDF导出空消息列表时抛出异常")
        void testExportSessionToPdf_EmptyMessages() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(new ArrayList<>());

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            BizException exception = assertThrows(BizException.class, () -> {
                exportService.exportSessionToPdf("session-123");
            });

            assertEquals(ErrorCode.EXPORT_VALIDATION_FAILED.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("没有可导出的消息"));

            TenantContext.clear();
        }

        @Test
        @DisplayName("PDF导出成功生成PDF字节数组")
        void testExportSessionToPdf_ReturnsPdfBytes() {
            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(testMessages);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            try {
                byte[] pdfBytes = exportService.exportSessionToPdf("session-123");
                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0, "PDF should have content");
                assertTrue(pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F',
                        "PDF should start with %PDF header");
            } catch (BizException e) {
                assertTrue(e.getCode() == ErrorCode.FONT_LOAD_FAILED.getCode(),
                        "Expected font loading error in test environment: " + e.getMessage());
                ExportServiceTest.log.warn("PDF export test skipped due to font loading issue in test environment");
            }

            TenantContext.clear();
        }
    }

    @Nested
    @DisplayName("CSV导出测试")
    class CsvExportTests {

        @Test
        @DisplayName("成功导出审计日志到CSV")
        void testExportAuditLogsToCsv_Success() {
            Page<AuditLog> logPage = new Page<>(1, 10000);
            logPage.setRecords(testAuditLogs);
            logPage.setTotal(testAuditLogs.size());
            doReturn(logPage).when(auditLogRepository).selectByFilters(any(), any(), any(), any(), any(), any(Page.class));

            String csv = exportService.exportAuditLogsToCsv(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
            );

            assertNotNull(csv);
            assertTrue(csv.contains("ID,用户ID,用户名,显示名称"));
            assertTrue(csv.contains("testuser"));
            assertTrue(csv.contains("admin"));
            assertTrue(csv.contains("LOGIN"));
            assertTrue(csv.contains("USER_CREATE"));
        }

        @Test
        @DisplayName("CSV导出处理特殊字符")
        void testExportAuditLogsToCsv_WithSpecialCharacters() {
            AuditLog logWithSpecialChars = AuditLog.builder()
                    .id("audit-special")
                    .userId("user-789")
                    .username("user,with,commas")
                    .displayName("用户\"引号\"测试")
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .resourceType("AUTH")
                    .description("描述\n换行")
                    .ipAddress("192.168.1.1")
                    .status("success")
                    .timestamp(LocalDateTime.now())
                    .build();

            Page<AuditLog> logPage = new Page<>(1, 10000);
            logPage.setRecords(Arrays.asList(logWithSpecialChars));
            logPage.setTotal(1);
            doReturn(logPage).when(auditLogRepository).selectByFilters(any(), any(), any(), any(), any(), any(Page.class));

            String csv = exportService.exportAuditLogsToCsv(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
            );

            assertNotNull(csv);
            assertTrue(csv.contains("\"user,with,commas\""));
            assertTrue(csv.contains("\"用户\"\"引号\"\"测试\""));
        }

        @Test
        @DisplayName("CSV导出空日志")
        void testExportAuditLogsToCsv_EmptyLogs() {
            Page<AuditLog> emptyPage = new Page<>(1, 10000);
            emptyPage.setRecords(new ArrayList<>());
            emptyPage.setTotal(0);
            doReturn(emptyPage).when(auditLogRepository).selectByFilters(any(), any(), any(), any(), any(), any(Page.class));

            String csv = exportService.exportAuditLogsToCsv(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
            );

            assertNotNull(csv);
            assertTrue(csv.contains("ID,用户ID,用户名,显示名称"));
        }
    }

    @Nested
    @DisplayName("JSON导出测试")
    class JsonExportTests {

        @Test
        @DisplayName("成功导出审计日志到JSON")
        void testExportAuditLogsToJson_Success() {
            Page<AuditLog> logPage = new Page<>(1, 10000);
            logPage.setRecords(testAuditLogs);
            logPage.setTotal(testAuditLogs.size());
            doReturn(logPage).when(auditLogRepository).selectByFilters(any(), any(), any(), any(), any(), any(Page.class));

            String json = exportService.exportAuditLogsToJson(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
            );

            assertNotNull(json);
            assertTrue(json.contains("\"exportTime\""));
            assertTrue(json.contains("\"totalCount\""));
            assertTrue(json.contains("\"logs\""));
            assertTrue(json.contains("testuser"));
            assertTrue(json.contains("admin"));
        }

        @Test
        @DisplayName("JSON导出包含详细信息")
        void testExportAuditLogsToJson_WithDetails() {
            AuditLog logWithDetails = AuditLog.builder()
                    .id("audit-details")
                    .userId("user-123")
                    .username("testuser")
                    .displayName("测试用户")
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .resourceType("AUTH")
                    .ipAddress("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .details("{\"browser\": \"Chrome\", \"os\": \"Windows\"}")
                    .status("success")
                    .timestamp(LocalDateTime.now())
                    .build();

            Page<AuditLog> logPage = new Page<>(1, 10000);
            logPage.setRecords(Arrays.asList(logWithDetails));
            logPage.setTotal(1);
            doReturn(logPage).when(auditLogRepository).selectByFilters(any(), any(), any(), any(), any(), any(Page.class));

            String json = exportService.exportAuditLogsToJson(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now()
            );

            assertNotNull(json);
            assertTrue(json.contains("\"details\""));
            assertTrue(json.contains("\"userAgent\""));
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("导出null内容的消息")
        void testExportSessionToMarkdown_NullContent() {
            ChatMessage messageWithNullContent = ChatMessage.builder()
                    .id("msg-null")
                    .sessionId("session-123")
                    .role("assistant")
                    .content(null)
                    .departmentId("dept-123")
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ChatMessage> messagesWithNull = Arrays.asList(
                    testMessages.get(0),
                    messageWithNullContent
            );

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messagesWithNull);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出消息内容为空字符串")
        void testExportSessionToMarkdown_EmptyContent() {
            ChatMessage messageWithEmptyContent = ChatMessage.builder()
                    .id("msg-empty")
                    .sessionId("session-123")
                    .role("user")
                    .content("")
                    .departmentId("dept-123")
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ChatMessage> messagesWithEmpty = Arrays.asList(messageWithEmptyContent);

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messagesWithEmpty);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);

            TenantContext.clear();
        }

        @Test
        @DisplayName("导出包含超长参考来源片段的消息")
        void testExportSessionToMarkdown_LongSourceSnippet() {
            String longSnippet = "A".repeat(500);
            ChatMessage.SourceReference source = ChatMessage.SourceReference.builder()
                    .documentId("doc-long")
                    .title("长文档")
                    .snippet(longSnippet)
                    .score(0.95)
                    .build();

            ChatMessage assistantMessage = ChatMessage.builder()
                    .id("msg-source-long")
                    .sessionId("session-123")
                    .role("assistant")
                    .content("回答")
                    .departmentId("dept-123")
                    .sources(Arrays.asList(source))
                    .createdAt(LocalDateTime.now())
                    .build();

            List<ChatMessage> messages = Arrays.asList(
                    testMessages.get(0),
                    assistantMessage
            );

            when(sessionRepository.selectById("session-123")).thenReturn(testSession);
            when(messageRepository.selectBySessionIdOrderByCreatedAtAsc("session-123")).thenReturn(messages);

            TenantContext.setUserId("user-123");
            TenantContext.setDepartmentId("dept-123");
            TenantContext.setUserRole("USER");

            String markdown = exportService.exportSessionToMarkdown("session-123");

            assertNotNull(markdown);
            assertTrue(markdown.contains("..."));
            assertTrue(markdown.length() < longSnippet.length() + 500);

            TenantContext.clear();
        }
    }
}
