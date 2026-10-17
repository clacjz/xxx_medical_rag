package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AISpan;
import com.clacjz.medical.rag.monitor.AITracingService;
import com.clacjz.medical.rag.model.dto.ChatQueryRequest;
import com.clacjz.medical.rag.model.dto.StreamEventDTO;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.Document;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.clacjz.medical.rag.model.dto.QueryResultDTO;
import com.clacjz.medical.rag.model.dto.SearchResult;
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
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RAGService 单元测试")
class RAGServiceTest {

    @Mock
    private HybridSearchService searchService;

    @Mock
    private LLMService llmService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private NerClient nerClient;

    @Mock
    private AITracingService aiTracingService;

    @Mock
    private AIMetricsService metricsService;

    @Mock
    private MedicalTermNormalizer medicalTermNormalizer;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private DocumentMapper documentMapper;

    private RAGService ragService;

    private SearchResult searchResult1;
    private SearchResult searchResult2;

    @BeforeEach
    void setUp() {
        // Manually construct RAGService with mocks
        ragService = new RAGService(searchService, llmService, embeddingService, ragProperties, nerClient, aiTracingService, metricsService, medicalTermNormalizer, collectionMapper, documentMapper);

        // Initialize metrics with a mock AIMetricsService
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        lenient().when(metricsService.getMeterRegistry()).thenReturn(meterRegistry);

        // Mock NER config (disabled by default)
        RAGProperties.NerConfig nerConfig = new RAGProperties.NerConfig();
        nerConfig.setEnabled(false);
        lenient().when(ragProperties.getNer()).thenReturn(nerConfig);

        // Mock QueryRewrite config (disabled by default)
        RAGProperties.QueryRewriteConfig queryRewriteConfig = new RAGProperties.QueryRewriteConfig();
        queryRewriteConfig.setEnabled(false);
        lenient().when(ragProperties.getQueryRewrite()).thenReturn(queryRewriteConfig);

        // Mock Document config (default supported types)
        lenient().when(ragProperties.getDocument()).thenReturn(new RAGProperties.DocumentConfig());

        // Mock MedicalTermNormalizer (passthrough by default)
        lenient().when(medicalTermNormalizer.normalize(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock aiTracingService to return proper AISpan objects (real instances, not mocks)
        doAnswer(invocation -> AISpan.builder()
                .spanId("test-span-id")
                .operationName(invocation.getArgument(1))
                .build())
                .when(aiTracingService).startSpan(anyString(), anyString(), anyString());

        // Mock embeddingService.tokenize to return token counts
        lenient().when(embeddingService.tokenize(anyString())).thenReturn(10);

        // Mock endSpan to return the input span (with duration set)
        doAnswer(invocation -> {
            AISpan span = invocation.getArgument(0);
            span.setDurationMs(100L);
            return span;
        }).when(aiTracingService).endSpan(any(AISpan.class));

        // Manually call initMetrics since @PostConstruct is not triggered in unit tests
        ragService.initMetrics();

        searchResult1 = SearchResult.builder()
                .id("doc-1")
                .documentId("doc-1")
                .collectionId("collection-1")
                .title("测试文档1")
                .text("这是测试文档的内容")
                .score(0.95)
                .build();

        searchResult2 = SearchResult.builder()
                .id("doc-2")
                .documentId("doc-2")
                .collectionId("collection-1")
                .title("测试文档2")
                .text("这是另一个测试文档的内容")
                .score(0.88)
                .build();
    }

    @Nested
    @DisplayName("query 方法测试")
    class QueryTests {

        @Test
        @DisplayName("query 成功 - 有搜索结果")
        void query_Success_WithResults() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("什么是Java")
                    .collectionIds(Arrays.asList("collection-1"))
                    .topK(10)
                    .similarityThreshold(0.5)
                    .noExpansion(false)
                    .build();

            List<SearchResult> searchResults = Arrays.asList(searchResult1, searchResult2);
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(searchResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("Java是一种编程语言", null));

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("Java是一种编程语言", result.getAnswer());
            assertNotNull(result.getSources());
            assertEquals(2, result.getSources().size());
            assertTrue(result.getGenerationTimeMs() >= 0);
        }

        @Test
        @DisplayName("query 成功 - 无搜索结果")
        void query_Success_NoResults() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("完全不相关的问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .noExpansion(true)
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("抱歉，我没有找到相关信息{{1}}", null));

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("抱歉，我没有找到相关信息{{1}}", result.getAnswer());
            assertNotNull(result.getSources());
            assertTrue(result.getSources().isEmpty());
        }

        @Test
        @DisplayName("query 成功 - 使用 noExpansion 模式")
        void query_Success_NoExpansion() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("什么是Spring")
                    .collectionIds(Arrays.asList("collection-1"))
                    .topK(5)
                    .noExpansion(true)
                    .build();

            List<SearchResult> searchResults = Arrays.asList(searchResult1);
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(searchResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("Spring是一个框架{{1}}", null));

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("Spring是一个框架{{1}}", result.getAnswer());
            assertEquals(1, result.getSources().size());
        }

        @Test
        @DisplayName("query 成功 - null topK 使用默认值")
        void query_Success_NullTopK() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .topK(null)
                    .build();

            when(searchService.search(anyString(), anyList(), eq(5), isNull(), isNull()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("测试回答", null));

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            verify(searchService).search(anyString(), anyList(), eq(5), isNull(), isNull());
        }

        @Test
        @DisplayName("query 成功 - null similarityThreshold")
        void query_Success_NullSimilarityThreshold() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .topK(10)
                    .similarityThreshold(null)
                    .build();

            when(searchService.search(anyString(), anyList(), eq(10), isNull(), isNull()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("测试回答", null));

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
        }

        @Test
        @DisplayName("query 成功 - 验证源文档信息正确映射")
        void query_Success_SourceMapping() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(Arrays.asList("collection-1"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("回答", null));

            QueryResultDTO result = ragService.query(request);

            assertEquals(1, result.getSources().size());
            assertEquals("doc-1", result.getSources().get(0).getDocumentId());
            assertEquals(0.95, result.getSources().get(0).getScore());
            assertEquals("collection-1", result.getSources().get(0).getCollectionId());
        }
    }

    @Nested
    @DisplayName("streamQuery 方法测试")
    class StreamQueryTests {

        @Test
        @DisplayName("streamQuery 成功 - 有搜索结果")
        void streamQuery_Success_WithResults() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("什么是微服务")
                    .collectionIds(Arrays.asList("collection-1"))
                    .topK(10)
                    .noExpansion(false)
                    .build();

            List<SearchResult> searchResults = Arrays.asList(searchResult1, searchResult2);
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(searchResults);
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("微").build(),
                            StreamEventDTO.builder().type("token").content("服务").build(),
                            StreamEventDTO.builder().type("token").content("是一").build(),
                            StreamEventDTO.builder().type("token").content("种架").build(),
                            StreamEventDTO.builder().type("token").content("构").build()
                    ));

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            // 1 classification + 2 source events + 5 token events
            assertEquals(8, events.size());
            assertEquals("classification", events.get(0).getType());
            assertEquals("source", events.get(1).getType());
            assertEquals("source", events.get(2).getType());
            assertEquals("token", events.get(3).getType());
            assertEquals("token", events.get(3).getType());
        }

        @Test
        @DisplayName("streamQuery 成功 - 无搜索结果")
        void streamQuery_Success_NoResults() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("不存在的问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(llmService.streamGenerate(contains("未检索到"), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("未").build(),
                            StreamEventDTO.builder().type("token").content("检").build(),
                            StreamEventDTO.builder().type("token").content("索").build(),
                            StreamEventDTO.builder().type("token").content("到").build()
                    ));

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            // 1 classification event + 4 token events
            assertEquals(5, events.size());
        }

        @Test
        @DisplayName("streamQuery 成功 - noExpansion 模式")
        void streamQuery_Success_NoExpansion() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(Arrays.asList("collection-1"))
                    .noExpansion(true)
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("测").build(),
                            StreamEventDTO.builder().type("token").content("试").build(),
                            StreamEventDTO.builder().type("token").content("回").build(),
                            StreamEventDTO.builder().type("token").content("答").build()
                    ));

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
        }

        @Test
        @DisplayName("streamQuery 成功 - 多个 collectionIds")
        void streamQuery_Success_MultipleCollections() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试问题")
                    .collectionIds(Arrays.asList("collection-1", "collection-2", "collection-3"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("回").build(),
                            StreamEventDTO.builder().type("token").content("答").build()
                    ));

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("上下文构建测试")
    class ContextBuildingTests {

        @Test
        @DisplayName("构建上下文 - 多个结果包含标题")
        void buildContext_MultipleResultsWithTitle() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(Arrays.asList("collection-1"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Arrays.asList(searchResult1, searchResult2));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("回答", null));

            ragService.query(request);

            verify(llmService).generateWithReasoning(argThat(prompt ->
                    prompt.contains("【文档 1】") &&
                    prompt.contains("标题：测试文档1") &&
                    prompt.contains("【文档 2】") &&
                    prompt.contains("标题：测试文档2")
            ), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("构建上下文 - 结果没有标题")
        void buildContext_ResultsWithoutTitle() {
            SearchResult noTitleResult = SearchResult.builder()
                    .id("doc-3")
                    .documentId("doc-3")
                    .text("无标题文档内容")
                    .score(0.9)
                    .build();

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(Arrays.asList("collection-1"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(noTitleResult));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean())).thenReturn(new LLMService.GenerateResult("回答", null));

            ragService.query(request);

            verify(llmService).generateWithReasoning(argThat(prompt ->
                    prompt.contains("【文档 1】") &&
                    prompt.contains("无标题文档内容") &&
                    !prompt.contains("标题：")
            ), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("构建上下文 - 空结果")
        void buildContext_EmptyResults() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(Arrays.asList("collection-1"))
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(llmService.generateWithReasoning(contains("未检索到与问题相关的内容"), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("无结果", null));

            ragService.query(request);

            verify(llmService).generateWithReasoning(contains("未检索到与问题相关的内容"), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Prompt 构建测试")
    class PromptBuildingTests {

        @Test
        @DisplayName("Prompt 构建 - noExpansion=true 使用严格模式")
        void promptBuilding_NoExpansion() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("严格问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .noExpansion(true)
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(argThat(prompt ->
                    prompt.contains("仅基于") && prompt.contains("不要编造")
            ), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("严格回答", null));

            ragService.query(request);
        }

        @Test
        @DisplayName("Prompt 构建 - noExpansion=false 使用扩展模式")
        void promptBuilding_WithExpansion() {
            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("扩展问题")
                    .collectionIds(Arrays.asList("collection-1"))
                    .noExpansion(false)
                    .build();

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(argThat(prompt ->
                    prompt.contains("结合") && prompt.contains("AI知识库")
            ), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("扩展回答", null));

            ragService.query(request);
        }

        @Test
        @DisplayName("Prompt 构建 - NEGATION_QUERY 追加否定语义提醒")
        void promptBuilding_Negation_AppendsReminder() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setTemperature(0.3);
            qrc.setHybridClassificationEnabled(true);
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：NEGATION_QUERY\n改写：高血压不能吃什么药");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(argThat(prompt ->
                    prompt.contains("否定/排除语义") && prompt.contains("不允许回答或推荐")
            ), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("高血压患者应避免高盐食物", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("高血压不能吃什么药")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);
        }
    }

    @Nested
    @DisplayName("元问题分类测试")
    class MetadataQueryTests {

        private Collection testCollection;

        @BeforeEach
        void setUpMetadata() {
            testCollection = Collection.builder()
                    .id("collection-1")
                    .name("测试知识库")
                    .description("这是一个测试知识库")
                    .documentCount(5)
                    .chunkCount(100L)
                    .totalSize(1024L * 1024L)
                    .build();

            // Enable query rewrite for metadata tests
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setTemperature(0.3);
            when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(testCollection));
        }

        @Test
        @DisplayName("query 元问题 - 知识库内容概览")
        void query_MetadataQuery_CollectionOverview() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：METADATA_QUERY\n改写：OVERVIEW");

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("这个知识库包含哪些内容")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("metadata", result.getQueryType());
            assertTrue(result.getAnswer().contains("测试知识库"));
            assertTrue(result.getAnswer().contains("5"));
            assertTrue(result.getAnswer().contains("100"));
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
            verify(llmService, never()).generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("query 元问题 - 最近更新")
        void query_MetadataQuery_RecentUpdates() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：METADATA_QUERY\n改写：RECENT_UPDATES");

            Document doc = Document.builder()
                    .id("doc-1")
                    .title("最新文档")
                    .fileName("new.pdf")
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(documentMapper.selectRecentByCollectionIds(anyList(), anyInt())).thenReturn(List.of(doc));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("最近更新了哪些文档")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("metadata", result.getQueryType());
            assertTrue(result.getAnswer().contains("最新文档"));
            assertTrue(result.getAnswer().contains("最近更新"));
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query 元问题 - 上传指南")
        void query_MetadataQuery_UploadGuide() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：METADATA_QUERY\n改写：UPLOAD_GUIDE");

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("怎么上传文档")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("metadata", result.getQueryType());
            assertTrue(result.getAnswer().contains("上传文档"));
            assertTrue(result.getAnswer().contains("知识库管理"));
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query 元问题 - 支持格式")
        void query_MetadataQuery_SupportedFormats() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：METADATA_QUERY\n改写：SUPPORTED_FORMATS");

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("支持什么格式")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("metadata", result.getQueryType());
            assertTrue(result.getAnswer().contains("PDF"));
            assertTrue(result.getAnswer().contains("Word"));
            assertTrue(result.getAnswer().contains("Markdown"));
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query 元问题 - rewrite 未启用时走检索流程")
        void query_MetadataQuery_RewriteDisabled() {
            // Disable rewrite (restore setUp default)
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(false);
            when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("检索回答", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("这个知识库包含哪些内容")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("检索回答", result.getAnswer());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("streamQuery 元问题 - 返回 metadata 事件和 token 流")
        void streamQuery_MetadataQuery() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：METADATA_QUERY\n改写：OVERVIEW");

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("包含哪些内容")
                    .collectionIds(List.of("collection-1"))
                    .build();

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            assertTrue(events.size() >= 2);
            assertEquals("metadata", events.get(0).getType());
            assertEquals("metadata", events.get(0).getQueryType());
            assertEquals("token", events.get(1).getType());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query 元问题 - LLM 返回异常格式时回退到检索")
        void query_MetadataQuery_FallbackOnParseFailure() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("some random non-standard response");

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("回退回答", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试问题")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("回退回答", result.getAnswer());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("检索类型路由测试（语义型/精确匹配型/混合型）")
    class RetrievalTypeRoutingTests {

        @BeforeEach
        void setUpRouting() {
            // Enable query rewrite + hybrid classification for routing tests
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setTemperature(0.3);
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);
        }

        @Test
        @DisplayName("query SEMANTIC_QUERY → 调用 searchVectorOnly，不调用 search")
        void query_Semantic_CallsVectorOnly() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写：布洛芬的作用机制");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("布洛芬通过抑制COX酶发挥解热镇痛作用", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬的作用机制是什么")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertTrue(result.getAnswer().contains("布洛芬"));
            assertEquals("semantic", result.getQueryType());
            verify(searchService).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchExactMatch(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query EXACT_MATCH_QUERY → 调用 searchExactMatch，不调用 search")
        void query_ExactMatch_CallsExactMatch() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：EXACT_MATCH_QUERY\n改写：北芪口服液一次喝多少");
            when(searchService.searchExactMatch(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("北芪口服液一次10ml", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("北芪口服液一次喝多少")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertTrue(result.getAnswer().contains("北芪口服液"));
            assertEquals("exact_match", result.getQueryType());
            verify(searchService).searchExactMatch(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query HYBRID_QUERY → 调用 search（当前行为），不调用专用方法")
        void query_Hybrid_CallsHybrid() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：HYBRID_QUERY\n改写：儿童高血压用药");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("儿童高血压常用药包括...", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("儿童高血压用哪种药比较好")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("hybrid", result.getQueryType());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchExactMatch(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query 旧格式 RETRIEVAL_QUERY → 回退到 HYBRID（调用 search）")
        void query_RetrievalFallback_CallsHybrid() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：RETRIEVAL_QUERY\n改写：高血压的治疗方法");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("高血压治疗方法包括...", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("高血压怎么治疗")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("hybrid", result.getQueryType());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("streamQuery SEMANTIC_QUERY → 发出 classification 事件和 source 事件")
        void streamQuery_Semantic_EmitsClassification() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写：阿莫西林和头孢的区别");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Arrays.asList(searchResult1, searchResult2));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("区").build(),
                            StreamEventDTO.builder().type("token").content("别").build()
                    ));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("阿莫西林和头孢的区别")
                    .collectionIds(List.of("collection-1"))
                    .build();

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            // classification + 2 source + 2 token
            assertEquals(5, events.size());
            assertEquals("classification", events.get(0).getType());
            assertEquals("semantic", events.get(0).getQueryType());
            assertEquals("source", events.get(1).getType());
            assertEquals("token", events.get(3).getType());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("streamQuery EXACT_MATCH_QUERY → 发出 exact_match classification 事件")
        void streamQuery_ExactMatch_EmitsClassification() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：EXACT_MATCH_QUERY\n改写：布洛芬的副作用");
            when(searchService.searchExactMatch(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("副").build(),
                            StreamEventDTO.builder().type("token").content("作").build(),
                            StreamEventDTO.builder().type("token").content("用").build()
                    ));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬的副作用")
                    .collectionIds(List.of("collection-1"))
                    .build();

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            assertEquals("classification", events.get(0).getType());
            assertEquals("exact_match", events.get(0).getQueryType());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("query NEGATION_QUERY → 走混合检索（同HYBRID），queryType=negation")
        void query_Negation_CallsHybrid() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：NEGATION_QUERY\n改写：高血压不能吃什么药");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("高血压患者应避免高盐食物", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("高血压不能吃什么药")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("negation", result.getQueryType());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchExactMatch(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("streamQuery NEGATION_QUERY → 发出 negation classification 事件")
        void streamQuery_Negation_EmitsClassification() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：NEGATION_QUERY\n改写：布洛芬不能和什么一起吃");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("禁").build(),
                            StreamEventDTO.builder().type("token").content("忌").build()
                    ));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬不能和什么一起吃")
                    .collectionIds(List.of("collection-1"))
                    .build();

            Flux<StreamEventDTO> result = ragService.streamQuery(request);

            assertNotNull(result);
            List<StreamEventDTO> events = result.collectList().block();
            assertNotNull(events);
            assertEquals("classification", events.get(0).getType());
            assertEquals("negation", events.get(0).getQueryType());
            verify(searchService).search(anyString(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("多查询扩展测试 (Multi-Query Expansion)")
    class MultiQueryExpansionTests {

        @BeforeEach
        void setUpMultiQuery() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setTemperature(0.3);
            qrc.setHybridClassificationEnabled(true);
            qrc.setMultiQueryEnabled(true);
            qrc.setMultiQueryCount(3);
            qrc.setMultiQueryMergeStrategy("MAX_SCORE");
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);
        }

        @Test
        @DisplayName("query: 3个查询变体 → 调用 3 次 search 后合并")
        void multiQuery_Enabled_ProducesMultipleSearches() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：HYBRID_QUERY\n改写1：布洛芬退热效果\n改写2：布洛芬 解热镇痛 退烧 药效\n改写3：布洛芬退烧");
            // 第一次 search 返回 2 个结果
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Arrays.asList(searchResult1, searchResult2));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("布洛芬有退热作用", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬退烧效果")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertTrue(result.getAnswer().contains("布洛芬"));
            // 应该调用 3 次 search（对应 3 个查询变体）
            verify(searchService, times(3)).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("streamQuery: 多查询扩展 → rewrite 事件携带 rewrittenQueries")
        void multiQuery_Stream_EventContainsRewrittenQueries() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写1：布洛芬退热效果\n改写2：布洛芬 解热镇痛\n改写3：布洛芬退烧");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.streamGenerate(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(Flux.just(
                            StreamEventDTO.builder().type("token").content("退").build(),
                            StreamEventDTO.builder().type("token").content("热").build()
                    ));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬退烧效果")
                    .collectionIds(List.of("collection-1"))
                    .build();

            Flux<StreamEventDTO> resultFlux = ragService.streamQuery(request);
            List<StreamEventDTO> events = resultFlux.collectList().block();
            assertNotNull(events);

            // 检查 rewrite 事件
            StreamEventDTO rewriteEvent = events.stream()
                    .filter(e -> "rewrite".equals(e.getType()))
                    .findFirst().orElse(null);
            assertNotNull(rewriteEvent, "Should have rewrite event");
            assertEquals("布洛芬退热效果", rewriteEvent.getRewrittenQuery());
            assertNotNull(rewriteEvent.getRewrittenQueries());
            assertEquals(3, rewriteEvent.getRewrittenQueries().size());
            assertEquals(Integer.valueOf(3), rewriteEvent.getQueryCount());

            // 应该调用 3 次 searchVectorOnly
            verify(searchService, times(3)).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("disabled: 回退为单 query 行为")
        void multiQuery_Disabled_BackwardCompatible() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setMultiQueryEnabled(false); // 禁用多查询
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：HYBRID_QUERY\n改写：布洛芬的功效");
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("布洛芬有退热功效", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬的功效")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);
            // 只调用 1 次 search
            verify(searchService, times(1)).search(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("多查询 + EXACT_MATCH → 调用 3 次 searchExactMatch")
        void multiQuery_WithExactMatch() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：EXACT_MATCH_QUERY\n改写1：布洛芬剂量\n改写2：布洛芬用法用量\n改写3：布洛芬规格");
            when(searchService.searchExactMatch(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("布洛芬一次10ml", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("布洛芬一次喝多少")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);

            verify(searchService, times(3)).searchExactMatch(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).search(anyString(), anyList(), anyInt(), any(), any());
            verify(searchService, never()).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("子查询分解测试 (Sub-Query Decomposition)")
    class SubQueryDecompositionTests {

        @BeforeEach
        void setUpDecomposition() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setTemperature(0.3);
            qrc.setHybridClassificationEnabled(true);
            qrc.setSubQueryDecompositionEnabled(true);
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);
        }

        @Test
        @DisplayName("复合问题（含『区别』）：LLM 返回分解后子查询，调用多次 search")
        void decomposition_CompoundQuery_Decomposed() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写1：甲亢的病因和治疗方法\n改写2：甲减的病因和治疗方法\n改写3：甲亢与甲减的临床差异");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("甲亢和甲减是两种不同的甲状腺疾病", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("甲亢和甲减的区别及各自的治疗方法")
                    .collectionIds(List.of("collection-1"))
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            // 验证分解后调用 3 次 searchVectorOnly
            verify(searchService, times(3)).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("非复合问题：LLM 返回单 query，调用一次 search")
        void decomposition_NonCompound_SingleQuery() {
            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写1：高血压的预防措施");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("高血压预防包括低盐饮食", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("高血压怎么预防")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);

            verify(searchService, times(1)).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("分解禁用：即使含『区别』也只调用一次 search")
        void decomposition_Disabled_NoEffect() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setSubQueryDecompositionEnabled(false); // 禁用分解
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写：甲亢和甲减的区别");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("甲亢和甲减的区别", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("甲亢和甲减的区别")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);

            // 禁用分解，只调用 1 次
            verify(searchService, times(1)).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("组合模式：分解+扩展同时启用，但总数受 cap 控制")
        void combined_DecompositionAndMultiQuery() {
            RAGProperties.QueryRewriteConfig qrc = new RAGProperties.QueryRewriteConfig();
            qrc.setEnabled(true);
            qrc.setMultiQueryEnabled(true);
            qrc.setMultiQueryCount(3);
            qrc.setSubQueryDecompositionEnabled(true);
            qrc.setMaxTotalSearchQueries(6);
            lenient().when(ragProperties.getQueryRewrite()).thenReturn(qrc);

            when(llmService.generate(anyString(), any(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                    .thenReturn("类型：SEMANTIC_QUERY\n改写1：甲亢治疗\n改写2：甲减治疗\n改写3：两者区别");
            when(searchService.searchVectorOnly(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(Collections.singletonList(searchResult1));
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("甲亢和甲减的治疗方法不同", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("甲亢和甲减的治疗区别")
                    .collectionIds(List.of("collection-1"))
                    .build();

            ragService.query(request);

            // 组合模式下，分解为 3 个子查询，每个未扩展（受 cap 控制）
            verify(searchService, times(3)).searchVectorOnly(anyString(), anyList(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("越界引用清理测试")
    class CitationCleanupTests {

        private List<SearchResult> fourResults;

        @BeforeEach
        void setUpCitationCleanup() {
            fourResults = Arrays.asList(
                    SearchResult.builder().id("doc-1").documentId("doc-1").collectionId("c-1").title("文档1").text("内容1").score(0.95).build(),
                    SearchResult.builder().id("doc-2").documentId("doc-2").collectionId("c-1").title("文档2").text("内容2").score(0.90).build(),
                    SearchResult.builder().id("doc-3").documentId("doc-3").collectionId("c-1").title("文档3").text("内容3").score(0.85).build(),
                    SearchResult.builder().id("doc-4").documentId("doc-4").collectionId("c-1").title("文档4").text("内容4").score(0.80).build()
            );
        }

        @Test
        @DisplayName("越界引用 {{5}} 在仅有4个结果时被移除")
        void outOfBounds_citation5_removedWhenOnly4Results() {
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(fourResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("根据文档{{5}}的描述，结果是肯定的", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(List.of("collection-1"))
                    .noExpansion(true)
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("根据文档{{5}}的描述，结果是肯定的", result.getAnswer());
        }

        @Test
        @DisplayName("有效引用 {{2}} 在4个结果时被保留")
        void validCitation_preservedWhenWithinBounds() {
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(fourResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("根据文档{{2}}的描述，结果是肯定的", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(List.of("collection-1"))
                    .noExpansion(true)
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("根据文档{{2}}的描述，结果是肯定的", result.getAnswer());
        }

        @Test
        @DisplayName("{{1}} 不会误伤 {{10}} — String.replace 是字面量替换而非正则")
        void citation1_doesNotCorruptCitation10() {
            List<SearchResult> tenResults = new java.util.ArrayList<>(fourResults);
            for (int i = 5; i <= 10; i++) {
                tenResults.add(SearchResult.builder().id("doc-" + i).documentId("doc-" + i).collectionId("c-1").title("文档" + i).text("内容" + i).score(0.75).build());
            }

            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(tenResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("文档{{1}}和文档{{10}}都有描述", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(List.of("collection-1"))
                    .noExpansion(true)
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("文档{{1}}和文档{{10}}都有描述", result.getAnswer());
        }

        @Test
        @DisplayName("多个越界引用全部被移除")
        void multipleOutOfBoundsCitations_notRemoved() {
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(fourResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("文档{{0}}和文档{{5}}以及文档{{9}}不存在", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(List.of("collection-1"))
                    .noExpansion(true)
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertEquals("文档{{0}}和文档{{5}}以及文档{{9}}不存在", result.getAnswer());
        }

        @Test
        @DisplayName("回答中无引用标记 — 追加AI来源告警后缀")
        void noCitationsInAnswer_appendsWarningSuffix() {
            when(searchService.search(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(fourResults);
            when(llmService.generateWithReasoning(anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), anyBoolean()))
                    .thenReturn(new LLMService.GenerateResult("这是一个没有任何引用标记的回答", null));

            ChatQueryRequest request = ChatQueryRequest.builder()
                    .query("测试")
                    .collectionIds(List.of("collection-1"))
                    .noExpansion(true)
                    .build();

            QueryResultDTO result = ragService.query(request);

            assertNotNull(result);
            assertTrue(result.getAnswer().startsWith("这是一个没有任何引用标记的回答"));
            assertTrue(result.getAnswer().contains("请参考检索文档核实具体信息"));
        }
    }
}
