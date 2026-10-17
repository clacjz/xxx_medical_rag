package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.ChatMessage;
import com.clacjz.medical.rag.model.entity.ChatSession;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.mapper.ChatMessageMapper;
import com.clacjz.medical.rag.mapper.ChatSessionMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.service.ChatService;
import com.clacjz.medical.rag.service.EmbeddingService;
import com.clacjz.medical.rag.service.RAGService;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AISpan;
import com.clacjz.medical.rag.monitor.AITracingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService 单元测试")
class ChatServiceTest {

    @Mock
    private ChatSessionMapper sessionRepository;

    @Mock
    private ChatMessageMapper messageRepository;

    @Mock
    private CollectionMapper collectionRepository;

    @Mock
    private RAGService ragService;

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private AITracingService aiTracingService;

    @Mock
    private AIMetricsService metricsService;

    @InjectMocks
    private ChatService chatService;

    private ChatSession testSession;
    private Collection testCollection;
    private String testUserId;
    private String testDepartmentId;

    @BeforeEach
    void setUp() {
        testUserId = "user-123";
        testDepartmentId = "dept-456";

        TenantContext.setUserId(testUserId);
        TenantContext.setDepartmentId(testDepartmentId);
        TenantContext.setUserRole("USER");

        testSession = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .title("Test Session")
                .userId(testUserId)
                .departmentId(testDepartmentId)
                .collectionIds(List.of("collection-1"))
                .status("ACTIVE")
                .messageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CollectionConfig config = new CollectionConfig();
        testCollection = Collection.builder()
                .id("collection-1")
                .name("Test Collection")
                .departmentId(testDepartmentId)
                .config(config)
                .build();

        RAGProperties.LLMConfig llmConfig = new RAGProperties.LLMConfig();
        llmConfig.setModel("test-model");
        llmConfig.setTemperature(0.7);
        llmConfig.setMaxTokens(1000);
        lenient().when(ragProperties.getLlm()).thenReturn(llmConfig);

        RAGProperties.EmbeddingConfig embeddingConfig = new RAGProperties.EmbeddingConfig();
        embeddingConfig.setModel("test-embedding");
        lenient().when(ragProperties.getEmbedding()).thenReturn(embeddingConfig);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("获取会话列表测试")
    class GetSessionsTests {

        @Test
        @DisplayName("获取会话列表成功 - 普通用户")
        void getSessions_Success_NormalUser() {
            Page<ChatSession> mpPage = new Page<>(1, 20);
            IPage<ChatSession> sessionPage = new PageImpl<>(List.of(testSession), mpPage);
            doReturn(sessionPage).when(sessionRepository).selectPage(any(Page.class), any());

            IPage<ChatSessionDTO> result = chatService.getSessions(0, 20);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(sessionRepository).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("获取会话列表成功 - 管理员用户")
        void getSessions_Success_AdminUser() {
            TenantContext.setUserRole("SYSTEM_ADMIN");
            TenantContext.setDepartmentId(testDepartmentId);

            Page<ChatSession> mpPage = new Page<>(1, 20);
            IPage<ChatSession> sessionPage = new PageImpl<>(List.of(testSession), mpPage);
            doReturn(sessionPage).when(sessionRepository).selectPage(any(Page.class), any());

            IPage<ChatSessionDTO> result = chatService.getSessions(0, 20);

            assertNotNull(result);
            verify(sessionRepository).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("获取会话列表返回空")
        void getSessions_EmptyResult() {
            Page<ChatSession> mpPage = new Page<>(1, 20);
            IPage<ChatSession> emptyPage = new PageImpl<>(List.of(), mpPage);
            doReturn(emptyPage).when(sessionRepository).selectPage(any(Page.class), any());

            IPage<ChatSessionDTO> result = chatService.getSessions(0, 20);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取会话详情测试")
    class GetSessionTests {

        @Test
        @DisplayName("获取会话详情成功")
        void getSession_Success() {
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);

            ChatSessionDTO result = chatService.getSession(testSession.getId());

            assertNotNull(result);
            assertEquals(testSession.getId(), result.getId());
            assertEquals(testSession.getTitle(), result.getTitle());
        }

        @Test
        @DisplayName("获取会话详情失败 - 会话不存在")
        void getSession_Failure_NotFound() {
            when(sessionRepository.selectById("nonexistent"))
                    .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.getSession("nonexistent"));

            assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("获取会话详情失败 - 无权访问")
        void getSession_Failure_PermissionDenied() {
            testSession.setUserId("other-user");
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.getSession(testSession.getId()));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
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
                    .collectionIds(List.of("collection-1"))
                    .build();

            when(collectionRepository.selectActiveByDepartmentId(testDepartmentId))
                    .thenReturn(List.of(testCollection));
            when(sessionRepository.insert(any(ChatSession.class)))
                    .thenAnswer(invocation -> {
                        ChatSession session = invocation.getArgument(0);
                        session.setId(UUID.randomUUID().toString());
                        return 1;
                    });

            ChatSessionDTO result = chatService.createSession(request);

            assertNotNull(result);
            assertEquals("New Session", result.getTitle());
        }

        @Test
        @DisplayName("创建会话失败 - 用户未关联部门")
        void createSession_Failure_NoDepartment() {
            TenantContext.setDepartmentId(null);

            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("New Session")
                    .build();

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.createSession(request));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        }

        @Test
        @DisplayName("创建会话失败 - Collection不存在")
        void createSession_Failure_CollectionNotFound() {
            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("New Session")
                    .collectionIds(List.of("nonexistent"))
                    .build();

            when(collectionRepository.selectActiveByDepartmentId(testDepartmentId))
                    .thenReturn(Collections.emptyList());

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.createSession(request));

            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("创建会话失败 - Collection不属于用户部门")
        void createSession_Failure_CollectionWrongDepartment() {
            testCollection.setDepartmentId("other-department");

            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .title("New Session")
                    .collectionIds(List.of("collection-1"))
                    .build();

            when(collectionRepository.selectActiveByDepartmentId(testDepartmentId))
                    .thenReturn(List.of(testCollection));

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.createSession(request));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }

        @Test
        @DisplayName("创建会话 - 自动生成标题")
        void createSession_AutoGenerateTitle() {
            CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                    .collectionIds(List.of("collection-1"))
                    .build();

            when(collectionRepository.selectActiveByDepartmentId(testDepartmentId))
                    .thenReturn(List.of(testCollection));
            when(sessionRepository.insert(any(ChatSession.class)))
                    .thenAnswer(invocation -> {
                        ChatSession session = invocation.getArgument(0);
                        session.setId(UUID.randomUUID().toString());
                        return 1;
                    });

            ChatSessionDTO result = chatService.createSession(request);

            assertNotNull(result);
            assertNotNull(result.getTitle());
            assertTrue(result.getTitle().startsWith("新会话"));
        }
    }

    @Nested
    @DisplayName("删除会话测试")
    class DeleteSessionTests {

        @Test
        @DisplayName("删除会话成功")
        void deleteSession_Success() {
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);
            when(sessionRepository.updateById(any(ChatSession.class)))
                    .thenReturn(1);

            assertDoesNotThrow(() -> chatService.deleteSession(testSession.getId()));

            verify(sessionRepository).updateById(argThat(session ->
                    "DELETED".equals(session.getStatus())));
        }

        @Test
        @DisplayName("删除会话失败 - 会话不存在")
        void deleteSession_Failure_NotFound() {
            when(sessionRepository.selectById("nonexistent"))
                    .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.deleteSession("nonexistent"));

            assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("更新会话Collection测试")
    class UpdateSessionCollectionsTests {

        @Test
        @DisplayName("更新会话Collection成功")
        void updateSessionCollections_Success() {
            List<String> newCollectionIds = List.of("collection-1", "collection-2");
            Collection collection2 = Collection.builder()
                    .id("collection-2")
                    .name("Collection 2")
                    .departmentId(testDepartmentId)
                    .build();

            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);
            when(collectionRepository.selectById("collection-1"))
                    .thenReturn(testCollection);
            when(collectionRepository.selectById("collection-2"))
                    .thenReturn(collection2);
            when(sessionRepository.updateById(any(ChatSession.class)))
                    .thenReturn(1);

            ChatSessionDTO result = chatService.updateSessionCollections(
                    testSession.getId(), newCollectionIds);

            assertNotNull(result);
            verify(sessionRepository).updateById(any(ChatSession.class));
        }

        @Test
        @DisplayName("更新会话Collection失败 - Collection不存在")
        void updateSessionCollections_Failure_CollectionNotFound() {
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);
            when(collectionRepository.selectById("nonexistent"))
                    .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> chatService.updateSessionCollections(testSession.getId(), List.of("nonexistent")));

            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("获取会话消息测试")
    class GetMessagesTests {

        @Test
        @DisplayName("获取会话消息成功")
        void getMessages_Success() {
            ChatMessage message = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId(testSession.getId())
                    .role("user")
                    .content("Hello")
                    .createdAt(LocalDateTime.now())
                    .build();

            Page<ChatMessage> mpPage = new Page<>(1, 50);
            IPage<ChatMessage> messagePage = new PageImpl<>(List.of(message), mpPage);
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);
            doReturn(messagePage).when(messageRepository).selectPage(any(Page.class), any());

            IPage<ChatMessageDTO> result = chatService.getMessages(testSession.getId(), 0, 50);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取会话消息返回空")
        void getMessages_EmptyResult() {
            Page<ChatMessage> mpPage = new Page<>(1, 50);
            IPage<ChatMessage> emptyPage = new PageImpl<>(List.of(), mpPage);
            when(sessionRepository.selectById(testSession.getId()))
                    .thenReturn(testSession);
            doReturn(emptyPage).when(messageRepository).selectPage(any(Page.class), any());

            IPage<ChatMessageDTO> result = chatService.getMessages(testSession.getId(), 0, 50);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("负数页码应抛出异常")
        void getSessions_NegativePage() {
            assertThrows(IllegalArgumentException.class, () ->
                    chatService.getSessions(-1, 20));
        }

        @Test
        @DisplayName("零页大小应抛出异常")
        void getSessions_ZeroPageSize() {
            assertThrows(IllegalArgumentException.class, () ->
                    chatService.getSessions(0, 0));
        }
    }

    @Nested
    @DisplayName("Token用量计算测试")
    class TokenUsageTests {

        private ChatQueryRequest buildQueryRequest(String sessionId) {
            return ChatQueryRequest.builder()
                    .sessionId(sessionId)
                    .query("测试查询")
                    .collectionIds(List.of("collection-1"))
                    .build();
        }

        private QueryResultDTO buildQueryResult(Integer tokensUsed, Integer promptTokens) {
            return QueryResultDTO.builder()
                    .answer("测试回答")
                    .tokensUsed(tokensUsed)
                    .promptTokens(promptTokens)
                    .generationTimeMs(100L)
                    .build();
        }

        private void setupTransactionMocks() {
            // Mock executeWithoutResult(Consumer<TransactionStatus>) for user message insert
            doAnswer(invocation -> {
                java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                        invocation.getArgument(0);
                consumer.accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());

            // Mock execute(TransactionCallback) for assistant message insert — returns the QueryResultDTO
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                org.springframework.transaction.support.TransactionCallback<QueryResultDTO> callback =
                        invocation.getArgument(0);
                return callback.doInTransaction(null);
            }).when(transactionTemplate).execute(any());
        }

        @Test
        @DisplayName("promptTokens和completionTokens均非空 - totalTokens为两者之和")
        void tokenUsage_BothNonNull_TotalIsSum() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            when(ragService.query(any(ChatQueryRequest.class)))
                    .thenReturn(buildQueryResult(80, 120));
            setupTransactionMocks();

            chatService.query(buildQueryRequest(testSession.getId()));

            verify(messageRepository).insert(argThat(msg -> {
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && Objects.equals(usage.getPromptTokens(), 120)
                        && Objects.equals(usage.getCompletionTokens(), 80)
                        && Objects.equals(usage.getTotalTokens(), 200);
            }));
        }

        @Test
        @DisplayName("promptTokens为null - totalTokens等于completionTokens")
        void tokenUsage_PromptTokensNull_TotalEqualsCompletion() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            when(ragService.query(any(ChatQueryRequest.class)))
                    .thenReturn(buildQueryResult(80, null));
            setupTransactionMocks();

            chatService.query(buildQueryRequest(testSession.getId()));

            verify(messageRepository).insert(argThat(msg -> {
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && usage.getPromptTokens() == null
                        && Objects.equals(usage.getCompletionTokens(), 80)
                        && Objects.equals(usage.getTotalTokens(), 80);
            }));
        }

        @Test
        @DisplayName("completionTokens为null - totalTokens等于promptTokens")
        void tokenUsage_CompletionTokensNull_TotalEqualsPrompt() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            when(ragService.query(any(ChatQueryRequest.class)))
                    .thenReturn(buildQueryResult(null, 120));
            setupTransactionMocks();

            chatService.query(buildQueryRequest(testSession.getId()));

            verify(messageRepository).insert(argThat(msg -> {
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && Objects.equals(usage.getPromptTokens(), 120)
                        && Objects.equals(usage.getCompletionTokens(), 0)
                        && Objects.equals(usage.getTotalTokens(), 120);
            }));
        }

        @Test
        @DisplayName("promptTokens和completionTokens均为null - totalTokens为0")
        void tokenUsage_BothNull_TotalIsZero() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            when(ragService.query(any(ChatQueryRequest.class)))
                    .thenReturn(buildQueryResult(null, null));
            setupTransactionMocks();

            chatService.query(buildQueryRequest(testSession.getId()));

            verify(messageRepository).insert(argThat(msg -> {
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && usage.getPromptTokens() == null
                        && Objects.equals(usage.getCompletionTokens(), 0)
                        && Objects.equals(usage.getTotalTokens(), 0);
            }));
        }
    }

    @Nested
    @DisplayName("流式查询边界测试")
    class StreamingEdgeCaseTests {

        private ChatQueryRequest buildStreamRequest(String sessionId) {
            return ChatQueryRequest.builder()
                    .sessionId(sessionId)
                    .query("测试流式查询")
                    .collectionIds(List.of("collection-1"))
                    .build();
        }

        private void setupStreamTransactionMocks() {
            // Mock executeWithoutResult for user message insert and assistant message save
            doAnswer(invocation -> {
                java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                        invocation.getArgument(0);
                consumer.accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());
        }

        private void setupTracingMocks() {
            AISpan mockSpan = AISpan.builder()
                    .traceId("test-trace")
                    .spanId("test-span")
                    .operationName("stream_query")
                    .spanType("stream_query")
                    .build();
            when(aiTracingService.startSpan(anyString(), anyString(), anyString())).thenReturn(mockSpan);
            when(aiTracingService.endSpan(any(AISpan.class))).thenReturn(mockSpan);
            when(aiTracingService.getTracingCoverage()).thenReturn(0.95);
        }

        @Test
        @DisplayName("streamQuery空响应 - 无token使用量")
        void streamQuery_emptyResponse_noTokenUsage() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            setupStreamTransactionMocks();
            setupTracingMocks();

            // Empty stream: no token or source events, just complete
            Flux<StreamEventDTO> emptyStream = Flux.empty();
            when(ragService.streamQuery(any(ChatQueryRequest.class), anyString()))
                    .thenReturn(emptyStream);
            when(embeddingService.tokenize(anyString())).thenReturn(0);

            Flux<StreamEventDTO> result = chatService.streamQuery(buildStreamRequest(testSession.getId()));

            // Block and collect all events from the stream
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);

            // Should contain a "done" event appended via concatWith
            boolean hasDoneEvent = events.stream().anyMatch(e ->
                    "done".equals(e.getType()) && e.getTokensUsed() != null && e.getTokensUsed() == 0);
            assertTrue(hasDoneEvent, "Should contain a done event with zero tokensUsed");

            // Verify assistant message was saved with zero tokens
            verify(messageRepository).insert(argThat(msg -> {
                if (!"assistant".equals(msg.getRole())) return false;
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && usage.getCompletionTokens() == 0
                        && usage.getTotalTokens() != null
                        && usage.getTotalTokens() >= 0;
            }));
        }

        @Test
        @DisplayName("streamQuery含推理内容 - 推理内容正确累积")
        void streamQuery_withReasoningContent() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            setupStreamTransactionMocks();
            setupTracingMocks();

            StreamEventDTO reasoningEvent1 = StreamEventDTO.builder()
                    .type("reasoning")
                    .content("第一步分析")
                    .build();
            StreamEventDTO reasoningEvent2 = StreamEventDTO.builder()
                    .type("reasoning")
                    .content("第二步推理")
                    .build();
            StreamEventDTO tokenEvent = StreamEventDTO.builder()
                    .type("token")
                    .content("最终答案")
                    .build();

            Flux<StreamEventDTO> stream = Flux.just(reasoningEvent1, reasoningEvent2, tokenEvent);
            when(ragService.streamQuery(any(ChatQueryRequest.class), anyString()))
                    .thenReturn(stream);
            when(embeddingService.tokenize(anyString())).thenReturn(4);

            Flux<StreamEventDTO> result = chatService.streamQuery(buildStreamRequest(testSession.getId()));

            // Block and collect all events from the stream
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);

            // Verify event types in order: reasoning, reasoning, token, done
            List<String> eventTypes = events.stream().map(StreamEventDTO::getType).collect(Collectors.toList());
            assertEquals(List.of("reasoning", "reasoning", "token", "done"), eventTypes);

            // Verify assistant message has accumulated reasoning content
            verify(messageRepository).insert(argThat(msg -> {
                if (!"assistant".equals(msg.getRole())) return false;
                return msg.getReasoning() != null
                        && msg.getReasoning().equals("第一步分析第二步推理");
            }));
        }

        @Test
        @DisplayName("streamQuery含来源引用 - 来源引用收集到ArrayList")
        void streamQuery_withSourceReferences() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            setupStreamTransactionMocks();
            setupTracingMocks();

            StreamEventDTO sourceEvent1 = StreamEventDTO.builder()
                    .type("source")
                    .documentId("doc-1")
                    .title("文档1")
                    .snippet("片段1")
                    .score(0.95)
                    .collectionId("collection-1")
                    .startPage(1)
                    .endPage(5)
                    .build();
            StreamEventDTO sourceEvent2 = StreamEventDTO.builder()
                    .type("source")
                    .documentId("doc-2")
                    .title("文档2")
                    .snippet("片段2")
                    .score(0.88)
                    .collectionId("collection-1")
                    .startPage(10)
                    .endPage(15)
                    .build();
            StreamEventDTO tokenEvent = StreamEventDTO.builder()
                    .type("token")
                    .content("基于来源的回答")
                    .build();

            Flux<StreamEventDTO> stream = Flux.just(sourceEvent1, sourceEvent2, tokenEvent);
            when(ragService.streamQuery(any(ChatQueryRequest.class), anyString()))
                    .thenReturn(stream);
            when(embeddingService.tokenize(anyString())).thenReturn(5);

            Flux<StreamEventDTO> result = chatService.streamQuery(buildStreamRequest(testSession.getId()));

            // Block and collect all events from the stream
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);

            // Verify source events and done event
            List<StreamEventDTO> sourceEvents = events.stream()
                    .filter(e -> "source".equals(e.getType()))
                    .collect(Collectors.toList());
            assertEquals(2, sourceEvents.size());
            assertEquals("doc-1", sourceEvents.get(0).getDocumentId());
            assertEquals("doc-2", sourceEvents.get(1).getDocumentId());
            assertTrue(events.stream().anyMatch(e -> "done".equals(e.getType())));

            // Verify assistant message has collected source references
            verify(messageRepository).insert(argThat(msg -> {
                if (!"assistant".equals(msg.getRole())) return false;
                List<ChatMessage.SourceReference> sources = msg.getSources();
                return sources != null
                        && sources.size() == 2
                        && "doc-1".equals(sources.get(0).getDocumentId())
                        && "doc-2".equals(sources.get(1).getDocumentId())
                        && sources.get(0).getScore() == 0.95
                        && sources.get(1).getScore() == 0.88;
            }));
        }

        @Test
        @DisplayName("query中promptTokens为null - totalTokens等于completionTokens")
        void query_withNullPromptTokens_totalTokensEqualsCompletion() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            QueryResultDTO result = QueryResultDTO.builder()
                    .answer("测试回答")
                    .tokensUsed(50)
                    .promptTokens(null)
                    .generationTimeMs(100L)
                    .build();
            when(ragService.query(any(ChatQueryRequest.class))).thenReturn(result);

            // Mock transaction template for both executeWithoutResult and execute
            doAnswer(invocation -> {
                java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                        invocation.getArgument(0);
                consumer.accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                org.springframework.transaction.support.TransactionCallback<QueryResultDTO> callback =
                        invocation.getArgument(0);
                return callback.doInTransaction(null);
            }).when(transactionTemplate).execute(any());

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .sessionId(testSession.getId())
                    .query("测试查询")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO queryResult = chatService.query(request);

            assertNotNull(queryResult);
            // Verify the assistant message has totalTokens = completionTokens when promptTokens is null
            verify(messageRepository).insert(argThat(msg -> {
                if (!"assistant".equals(msg.getRole())) return false;
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && usage.getPromptTokens() == null
                        && Objects.equals(usage.getCompletionTokens(), 50)
                        && Objects.equals(usage.getTotalTokens(), 50);
            }));
        }

        @Test
        @DisplayName("query中token数为0 - totalTokens为0")
        void query_withZeroTokens_totalTokensIsZero() {
            when(sessionRepository.selectById(testSession.getId())).thenReturn(testSession);
            QueryResultDTO result = QueryResultDTO.builder()
                    .answer("测试回答")
                    .tokensUsed(0)
                    .promptTokens(0)
                    .generationTimeMs(100L)
                    .build();
            when(ragService.query(any(ChatQueryRequest.class))).thenReturn(result);

            doAnswer(invocation -> {
                java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                        invocation.getArgument(0);
                consumer.accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                org.springframework.transaction.support.TransactionCallback<QueryResultDTO> callback =
                        invocation.getArgument(0);
                return callback.doInTransaction(null);
            }).when(transactionTemplate).execute(any());

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .sessionId(testSession.getId())
                    .query("测试查询")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO queryResult = chatService.query(request);

            assertNotNull(queryResult);
            // Verify the assistant message has totalTokens = 0 when both are 0
            verify(messageRepository).insert(argThat(msg -> {
                if (!"assistant".equals(msg.getRole())) return false;
                ChatMessage.TokenUsage usage = msg.getTokenUsage();
                return usage != null
                        && Objects.equals(usage.getPromptTokens(), 0)
                        && Objects.equals(usage.getCompletionTokens(), 0)
                        && Objects.equals(usage.getTotalTokens(), 0);
            }));
        }
    }

    // Helper class to convert IPage to PageImpl-like structure
    static class PageImpl<T> extends Page<T> {
        private final List<T> records;
        private final long total;

        PageImpl(List<T> records, Page<T> mpPage) {
            super(mpPage.getCurrent(), mpPage.getSize());
            this.records = records;
            this.total = records.size();
            this.setRecords(records);
        }

        @Override
        public List<T> getRecords() {
            return records;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }
}
