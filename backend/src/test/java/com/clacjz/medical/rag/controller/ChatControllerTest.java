package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.ChatService;
import com.clacjz.medical.rag.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController 单元测试")
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ExportService exportService;

    @Mock
    private com.clacjz.medical.rag.util.JsonUtil jsonUtil;

    @InjectMocks
    private ChatController chatController;

    private MockHttpServletRequest mockRequest;
    private UserDetails mockUserDetails;
    private ChatSessionDTO testSession;
    private ChatMessageDTO testMessage;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);

        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        testSession = ChatSessionDTO.builder()
                .id(UUID.randomUUID().toString())
                .title("Test Session")
                .userId("user-123")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        testMessage = ChatMessageDTO.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(testSession.getId())
                .role("user")
                .content("Hello")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("获取会话列表测试")
    class ListSessionsTests {

        @Test
        @DisplayName("获取会话列表成功 - 默认参数")
        void listSessions_Success_DefaultParams() {
            Page<ChatSessionDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testSession));
            page.setTotal(1);
            IPage<ChatSessionDTO> sessions = page;
            when(chatService.getSessions(0, 20)).thenReturn(sessions);

            ResponseEntity<Result<PageResult<ChatSessionDTO>>> response =
                    chatController.listSessions(0, 20, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(1, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("获取会话列表成功 - 自定义分页")
        void listSessions_Success_CustomPagination() {
            Page<ChatSessionDTO> page = new Page<>(3, 50);
            page.setRecords(List.of(testSession));
            page.setTotal(1);
            IPage<ChatSessionDTO> sessions = page;
            when(chatService.getSessions(2, 50)).thenReturn(sessions);

            ResponseEntity<Result<PageResult<ChatSessionDTO>>> response =
                    chatController.listSessions(2, 50, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取会话列表返回空结果")
        void listSessions_EmptyResult() {
            Page<ChatSessionDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<ChatSessionDTO> sessions = emptyPage;
            when(chatService.getSessions(anyInt(), anyInt())).thenReturn(sessions);

            ResponseEntity<Result<PageResult<ChatSessionDTO>>> response =
                    chatController.listSessions(0, 20, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("获取会话列表失败 - 用户未认证")
        void listSessions_Failure_NotAuthenticated() {
            assertThrows(BizException.class,
                    () -> chatController.listSessions(0, 20, null));
        }
    }

    @Nested
    @DisplayName("获取会话详情测试")
    class GetSessionTests {

        @Test
        @DisplayName("获取会话详情成功")
        void getSession_Success() {
            String sessionId = testSession.getId();
            when(chatService.getSession(sessionId)).thenReturn(testSession);

            ResponseEntity<Result<ChatSessionDTO>> response =
                    chatController.getSession(sessionId, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testSession.getId(), response.getBody().getData().getId());
        }

        @Test
        @DisplayName("获取会话详情失败 - 会话不存在")
        void getSession_Failure_SessionNotFound() {
            String sessionId = "nonexistent";
            when(chatService.getSession(sessionId)).thenThrow(
                    new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在")
            );

            assertThrows(BizException.class,
                    () -> chatController.getSession(sessionId, mockUserDetails));
        }

        @Test
        @DisplayName("获取会话详情失败 - 用户信息为空")
        void getSession_Failure_NullUserDetails() {
            assertThrows(BizException.class,
                    () -> chatController.getSession("session-id", null));
        }
    }

    @Nested
    @DisplayName("创建会话测试")
    class CreateSessionTests {

        @Test
        @DisplayName("创建会话成功")
        void createSession_Success() {
            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("New Session")
                    .collectionIds(List.of("collection-1", "collection-2"))
                    .build();

            ChatSessionDTO newSession = ChatSessionDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .title("New Session")
                    .userId("user-123")
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            when(chatService.createSession(request)).thenReturn(newSession);

            ResponseEntity<Result<ChatSessionDTO>> response =
                    chatController.createSession(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("会话创建成功", response.getBody().getMessage());
            assertEquals("New Session", response.getBody().getData().getTitle());
        }

        @Test
        @DisplayName("创建会话成功 - 无Collection")
        void createSession_Success_NoCollections() {
            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("Session Without Collections")
                    .build();

            when(chatService.createSession(request)).thenReturn(testSession);

            ResponseEntity<Result<ChatSessionDTO>> response =
                    chatController.createSession(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("创建会话失败 - 标题为空")
        void createSession_Failure_EmptyTitle() {
            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("")
                    .build();

            when(chatService.createSession(request)).thenThrow(
                    new BizException(ErrorCode.INVALID_PARAMETER, "会话标题不能为空")
            );

            assertThrows(BizException.class,
                    () -> chatController.createSession(request, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("删除会话测试")
    class DeleteSessionTests {

        @Test
        @DisplayName("删除会话成功")
        void deleteSession_Success() {
            String sessionId = testSession.getId();
            doNothing().when(chatService).deleteSession(sessionId);

            ResponseEntity<Result<Void>> response =
                    chatController.deleteSession(sessionId, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("会话删除成功", response.getBody().getMessage());
            verify(chatService, times(1)).deleteSession(sessionId);
        }

        @Test
        @DisplayName("删除会话失败 - 会话不存在")
        void deleteSession_Failure_SessionNotFound() {
            String sessionId = "nonexistent";
            doThrow(new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在"))
                    .when(chatService).deleteSession(sessionId);

            assertThrows(BizException.class,
                    () -> chatController.deleteSession(sessionId, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("更新会话Collection测试")
    class UpdateSessionCollectionsTests {

        @Test
        @DisplayName("更新会话Collection成功")
        void updateSessionCollections_Success() {
            String sessionId = testSession.getId();
            List<String> collectionIds = List.of("col-1", "col-2");

            ChatSessionDTO updatedSession = ChatSessionDTO.builder()
                    .id(sessionId)
                    .title("Updated Session")
                    .build();

            when(chatService.updateSessionCollections(sessionId, collectionIds))
                    .thenReturn(updatedSession);

            ResponseEntity<Result<ChatSessionDTO>> response =
                    chatController.updateSessionCollections(sessionId, collectionIds, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("会话Collection更新成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("更新会话Collection - 清空Collection")
        void updateSessionCollections_ClearCollections() {
            String sessionId = testSession.getId();
            List<String> emptyCollections = List.of();

            when(chatService.updateSessionCollections(sessionId, emptyCollections))
                    .thenReturn(testSession);

            ResponseEntity<Result<ChatSessionDTO>> response =
                    chatController.updateSessionCollections(sessionId, emptyCollections, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("获取会话消息测试")
    class GetMessagesTests {

        @Test
        @DisplayName("获取会话消息成功")
        void getMessages_Success() {
            String sessionId = testSession.getId();
            Page<ChatMessageDTO> page = new Page<>(1, 50);
            page.setRecords(List.of(testMessage));
            page.setTotal(1);
            IPage<ChatMessageDTO> messages = page;
            when(chatService.getMessages(sessionId, 0, 50)).thenReturn(messages);

            ResponseEntity<Result<PageResult<ChatMessageDTO>>> response =
                    chatController.getMessages(sessionId, 0, 50, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("获取会话消息成功 - 自定义分页")
        void getMessages_Success_CustomPagination() {
            String sessionId = testSession.getId();
            Page<ChatMessageDTO> page = new Page<>(6, 100);
            page.setRecords(List.of(testMessage));
            page.setTotal(1);
            IPage<ChatMessageDTO> messages = page;
            when(chatService.getMessages(sessionId, 5, 100)).thenReturn(messages);

            ResponseEntity<Result<PageResult<ChatMessageDTO>>> response =
                    chatController.getMessages(sessionId, 5, 100, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取会话消息返回空结果")
        void getMessages_EmptyResult() {
            String sessionId = testSession.getId();
            Page<ChatMessageDTO> emptyPage = new Page<>(1, 50);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<ChatMessageDTO> messages = emptyPage;
            when(chatService.getMessages(any(), anyInt(), anyInt())).thenReturn(messages);

            ResponseEntity<Result<PageResult<ChatMessageDTO>>> response =
                    chatController.getMessages(sessionId, 0, 50, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().getTotal());
        }
    }

    @Nested
    @DisplayName("RAG问答测试")
    class QueryTests {

        @Test
        @DisplayName("问答查询成功")
        void query_Success() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .sessionId(testSession.getId())
                    .query("什么是糖尿病？")
                    .collectionIds(List.of("col-1"))
                    .noExpansion(false)
                    .build();

            QueryResultDTO queryResult = QueryResultDTO.builder()
                    .answer("糖尿病是一种慢性疾病...")
                    .sources(List.of())
                    .build();

            when(chatService.query(request)).thenReturn(queryResult);

            ResponseEntity<Result<QueryResultDTO>> response =
                    chatController.query(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("糖尿病是一种慢性疾病...", response.getBody().getData().getAnswer());
        }

        @Test
        @DisplayName("问答查询成功 - 新会话")
        void query_Success_NewSession() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("今天天气如何？")
                    .collectionIds(List.of())
                    .noExpansion(true)
                    .build();

            QueryResultDTO queryResult = QueryResultDTO.builder()
                    .answer("我无法获取天气信息...")
                    .build();

            when(chatService.query(request)).thenReturn(queryResult);

            ResponseEntity<Result<QueryResultDTO>> response =
                    chatController.query(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("问答查询失败 - 问题为空")
        void query_Failure_EmptyQuery() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("")
                    .build();

            when(chatService.query(request)).thenThrow(
                    new BizException(ErrorCode.INVALID_PARAMETER, "问题不能为空")
            );

            assertThrows(BizException.class,
                    () -> chatController.query(request, mockUserDetails));
        }

        @Test
        @DisplayName("问答查询失败 - 问题过长")
        void query_Failure_QueryTooLong() {
            String longQuery = "a".repeat(5000);
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query(longQuery)
                    .build();

            when(chatService.query(request)).thenThrow(
                    new BizException(ErrorCode.INVALID_PARAMETER, "问题长度不能超过2000字符")
            );

            assertThrows(BizException.class,
                    () -> chatController.query(request, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("流式问答测试")
    class StreamQueryTests {

        @Test
        @DisplayName("流式问答成功创建SSEEmitter")
        void streamQuery_Success_CreatesEmitter() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .sessionId(testSession.getId())
                    .query("解释量子计算")
                    .collectionIds(List.of("col-1"))
                    .noExpansion(false)
                    .build();

            when(chatService.streamQuery(request))
                    .thenReturn(reactor.core.publisher.Flux.just(
                            StreamEventDTO.builder().type("token").content("量子").build(),
                            StreamEventDTO.builder().type("token").content("计算").build(),
                            StreamEventDTO.builder().type("token").content("是").build()));

            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                    chatController.streamQuery(request, mockUserDetails);

            assertNotNull(emitter);
            assertEquals(600000L, emitter.getTimeout());
        }

        @Test
        @DisplayName("流式问答 - 新会话创建Emitter")
        void streamQuery_NewSession_CreatesEmitter() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("你好")
                    .collectionIds(List.of())
                    .build();

            when(chatService.streamQuery(request))
                    .thenReturn(reactor.core.publisher.Flux.just(
                            StreamEventDTO.builder().type("token").content("你").build(),
                            StreamEventDTO.builder().type("token").content("好").build()));

            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                    chatController.streamQuery(request, mockUserDetails);

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("流式问答 - 长查询")
        void streamQuery_LongQuery_CreatesEmitter() {
            String longQuery = "请详细解释一下人工智能、机器学习和深度学习之间的关系和区别";
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query(longQuery)
                    .collectionIds(List.of("col-1", "col-2", "col-3"))
                    .noExpansion(false)
                    .build();

            when(chatService.streamQuery(request))
                    .thenReturn(reactor.core.publisher.Flux.empty());

            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                    chatController.streamQuery(request, mockUserDetails);

            assertNotNull(emitter);
        }
    }

    @Nested
    @DisplayName("边界值和异常测试")
    class BoundaryAndExceptionTests {

        @Test
        @DisplayName("空用户名获取会话列表")
        void listSessions_EmptyUsername() {
            assertThrows(BizException.class,
                    () -> chatController.listSessions(0, 20, null));
        }

        @Test
        @DisplayName("无效分页参数 - 负数页码")
        void getUserList_NegativePage() {
            Page<ChatSessionDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testSession));
            page.setTotal(1);
            IPage<ChatSessionDTO> sessions = page;
            when(chatService.getSessions(-1, 20)).thenReturn(sessions);

            ResponseEntity<Result<PageResult<ChatSessionDTO>>> response =
                    chatController.listSessions(-1, 20, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("零页大小")
        void getUserList_ZeroPageSize() {
            Page<ChatSessionDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<ChatSessionDTO> sessions = emptyPage;
            when(chatService.getSessions(0, 0)).thenReturn(sessions);

            ResponseEntity<Result<PageResult<ChatSessionDTO>>> response =
                    chatController.listSessions(0, 0, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("导出会话测试")
    class ExportSessionTests {

        @Test
        @DisplayName("导出Markdown格式成功")
        void exportSession_Markdown_Success() {
            String sessionId = testSession.getId();
            String markdownContent = "# 测试会话\n\n## 用户\n\n问题内容";
            
            when(exportService.exportSessionToMarkdown(sessionId)).thenReturn(markdownContent);

            ResponseEntity<?> response = chatController.exportSession(sessionId, "markdown", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody() instanceof String);
            assertTrue(((String) response.getBody()).contains("# 测试会话"));
            
            HttpHeaders headers = response.getHeaders();
            assertTrue(headers.getContentType().toString().contains("text/markdown"));
        }

        @Test
        @DisplayName("导出PDF格式成功")
        void exportSession_Pdf_Success() {
            String sessionId = testSession.getId();
            byte[] pdfContent = "%PDF-1.4 fake pdf content".getBytes();
            
            when(exportService.exportSessionToPdf(sessionId)).thenReturn(pdfContent);

            ResponseEntity<?> response = chatController.exportSession(sessionId, "pdf", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody() instanceof byte[]);
            
            HttpHeaders headers = response.getHeaders();
            assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
        }

        @Test
        @DisplayName("导出默认格式为Markdown")
        void exportSession_DefaultFormat_Markdown() {
            String sessionId = testSession.getId();
            String markdownContent = "# 会话内容";
            
            when(exportService.exportSessionToMarkdown(sessionId)).thenReturn(markdownContent);

            ResponseEntity<?> response = chatController.exportSession(sessionId, "markdown", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(exportService).exportSessionToMarkdown(sessionId);
        }

        @Test
        @DisplayName("导出忽略大小写格式参数")
        void exportSession_CaseInsensitiveFormat() {
            String sessionId = testSession.getId();
            byte[] pdfContent = "%PDF-1.4 fake".getBytes();
            
            when(exportService.exportSessionToPdf(sessionId)).thenReturn(pdfContent);

            ResponseEntity<?> response = chatController.exportSession(sessionId, "PDF", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(exportService).exportSessionToPdf(sessionId);
        }

        @Test
        @DisplayName("导出失败 - 会话不存在")
        void exportSession_Failure_SessionNotFound() {
            String sessionId = "nonexistent";
            
            when(exportService.exportSessionToMarkdown(sessionId))
                    .thenThrow(new RuntimeException("会话不存在: " + sessionId));

            assertThrows(RuntimeException.class,
                    () -> chatController.exportSession(sessionId, "markdown", mockUserDetails));
        }

        @Test
        @DisplayName("导出失败 - 用户未认证")
        void exportSession_Failure_NotAuthenticated() {
            assertThrows(BizException.class,
                    () -> chatController.exportSession("session-id", "markdown", null));
        }

        @Test
        @DisplayName("导出带空格的会话ID")
        void exportSession_SessionIdWithSpaces() {
            String sessionId = "session with spaces";
            String markdownContent = "# 内容";
            
            when(exportService.exportSessionToMarkdown(sessionId)).thenReturn(markdownContent);

            ResponseEntity<?> response = chatController.exportSession(sessionId, "markdown", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}
