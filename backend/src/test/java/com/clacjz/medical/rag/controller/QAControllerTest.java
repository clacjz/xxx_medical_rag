package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.ChatQueryRequest;
import com.clacjz.medical.rag.model.dto.ChatMessageDTO;
import com.clacjz.medical.rag.model.dto.ChatSessionDTO;
import com.clacjz.medical.rag.model.dto.QueryResultDTO;
import com.clacjz.medical.rag.model.dto.StreamEventDTO;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QAController 单元测试")
class QAControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private com.clacjz.medical.rag.service.ExportService exportService;

    @Mock
    private com.clacjz.medical.rag.util.JsonUtil jsonUtil;

    @InjectMocks
    private ChatController chatController;

    private UserDetails mockUserDetails;
    private ChatSessionDTO testSession;

    @BeforeEach
    void setUp() {
        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        testSession = ChatSessionDTO.builder()
                .id(UUID.randomUUID().toString())
                .title("Test Conversation")
                .userId("user-123")
                .build();
    }

    @Test
    @DisplayName("获取会话列表成功")
    void listConversations_Success() {
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
    }

    @Test
    @DisplayName("获取会话详情成功")
    void getConversation_Success() {
        String sessionId = testSession.getId();
        when(chatService.getSession(sessionId)).thenReturn(testSession);

        ResponseEntity<Result<ChatSessionDTO>> response =
                chatController.getSession(sessionId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("获取会话消息成功")
    void getConversationMessages_Success() {
        String sessionId = testSession.getId();
        Page<ChatMessageDTO> emptyPage = new Page<>(1, 50);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        IPage<ChatMessageDTO> messages = emptyPage;
        when(chatService.getMessages(sessionId, 0, 50)).thenReturn(messages);

        ResponseEntity<Result<PageResult<ChatMessageDTO>>> response =
                chatController.getMessages(sessionId, 0, 50, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("问答查询成功")
    void query_Success() {
        ChatQueryRequest request = ChatQueryRequest.builder()
                .sessionId(testSession.getId())
                .query("什么是糖尿病？")
                .build();

        QueryResultDTO queryResult = QueryResultDTO.builder()
                .answer("糖尿病是一种慢性疾病...")
                .sources(List.of())
                .build();

        when(chatService.query(request)).thenReturn(queryResult);

        ResponseEntity<Result<QueryResultDTO>> response =
                chatController.query(request, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("问答查询失败")
    void query_Failure() {
        ChatQueryRequest request = ChatQueryRequest.builder()
                .query("test")
                .build();

        when(chatService.query(request)).thenThrow(
                new BizException(ErrorCode.INVALID_PARAMETER, "问题不能为空")
        );

        assertThrows(BizException.class,
                () -> chatController.query(request, mockUserDetails));
    }

    @Test
    @DisplayName("流式问答成功创建SSEEmitter")
    void streamQuery_Success() {
        ChatQueryRequest request = ChatQueryRequest.builder()
                .sessionId(testSession.getId())
                .query("测试查询")
                .build();

        when(chatService.streamQuery(request))
                .thenReturn(reactor.core.publisher.Flux.just(
                        StreamEventDTO.builder().type("token").content("结果").build()));

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                chatController.streamQuery(request, mockUserDetails);

        assertNotNull(emitter);
    }
}
