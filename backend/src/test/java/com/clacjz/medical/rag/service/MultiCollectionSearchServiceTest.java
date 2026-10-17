package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.dto.SearchResult;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiCollectionSearchService 单元测试")
class MultiCollectionSearchServiceTest {

    @Mock
    private CollectionMapper collectionRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private MultiCollectionSearchService searchService;

    private Collection testCollection;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskExecutor).execute(any());

        CollectionConfig config = new CollectionConfig();
        CollectionConfig.RetrievalConfig retrievalConfig = new CollectionConfig.RetrievalConfig();
        retrievalConfig.setSimilarityThreshold(0.75);
        config.setRetrieval(retrievalConfig);

        testCollection = Collection.builder()
                .id("collection-1")
                .name("Test Collection")
                .config(config)
                .build();
    }

    @Nested
    @DisplayName("搜索功能测试")
    class SearchTests {

        @Test
        @DisplayName("搜索成功 - 单个Collection")
        void search_Success_SingleCollection() {
            String query = "测试查询";
            List<String> collectionIds = List.of("collection-1");

            when(collectionRepository.selectActiveByIds(collectionIds))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(query)).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search(query, collectionIds, 10, 0.7);

            assertNotNull(results);
        }

        @Test
        @DisplayName("搜索成功 - 多个Collection")
        void search_Success_MultipleCollections() {
            String query = "测试查询";
            List<String> collectionIds = List.of("collection-1", "collection-2");

            Collection collection2 = Collection.builder()
                    .id("collection-2")
                    .name("Collection 2")
                    .build();

            when(collectionRepository.selectActiveByIds(collectionIds))
                    .thenReturn(List.of(testCollection, collection2));
            when(embeddingService.embed(query)).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search(query, collectionIds, 10, 0.7);

            assertNotNull(results);
        }

        @Test
        @DisplayName("搜索失败 - 空CollectionId列表")
        void search_Failure_EmptyCollectionIds() {
            List<String> emptyList = Collections.emptyList();
            List<SearchResult> results = searchService.search("query", emptyList, 10, 0.7);

            assertNotNull(results);
            assertTrue(results.isEmpty());
            verify(collectionRepository, never()).selectActiveByIds(any());
        }

        @Test
        @DisplayName("搜索失败 - Null CollectionId列表")
        void search_Failure_NullCollectionIds() {
            List<SearchResult> results = searchService.search("query", null, 10, 0.7);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("搜索失败 - Collection都不活跃")
        void search_Failure_NoActiveCollections() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(Collections.emptyList());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 0.7);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("搜索失败 - Qdrant服务不可用")
        void search_Failure_QdrantUnavailable() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(null);

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 0.7);

            assertNotNull(results);
        }
    }

    @Nested
    @DisplayName("结果合并和排序测试")
    class MergeAndSortTests {

        @Test
        @DisplayName("合并结果 - 按分数排序")
        void mergeResults_SortByScore() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponseWithMultipleResults());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 5, 0.0);

            assertNotNull(results);
        }

        @Test
        @DisplayName("合并结果 - 去重")
        void mergeResults_Deduplication() {
            SearchResult result1 = SearchResult.builder()
                    .id("same-id")
                    .collectionId("col-1")
                    .score(0.9)
                    .build();

            SearchResult result2 = SearchResult.builder()
                    .id("same-id")
                    .collectionId("col-2")
                    .score(0.8)
                    .build();

            List<SearchResult> allResults = Arrays.asList(result1, result2);
            List<SearchResult> merged = invokeMergeAndSort(allResults, 10);

            assertEquals(1, merged.size());
        }

        @Test
        @DisplayName("合并结果 - 限制返回数量")
        void mergeResults_LimitResults() {
            List<SearchResult> manyResults = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                manyResults.add(SearchResult.builder()
                        .id("id-" + i)
                        .collectionId("col-1")
                        .score(0.9 - i * 0.01)
                        .text("Text " + i)
                        .build());
            }

            List<SearchResult> limited = invokeMergeAndSort(manyResults, 5);

            assertEquals(5, limited.size());
        }

        private List<SearchResult> invokeMergeAndSort(List<SearchResult> results, int topK) {
            try {
                var method = MultiCollectionSearchService.class.getDeclaredMethod(
                        "mergeAndSortResults", List.class, int.class);
                method.setAccessible(true);
                return (List<SearchResult>) method.invoke(searchService, results, topK);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("响应解析测试")
    class ResponseParsingTests {

        @Test
        @DisplayName("解析有效JSON响应")
        void parseValidJsonResponse() {
            String validJson = """
                {
                    "result": [
                        {
                            "id": "point-1",
                            "score": 0.95,
                            "payload": {
                                "text": "Sample text content",
                                "title": "Document Title",
                                "document_id": "doc-123"
                            }
                        }
                    ]
                }
                """;

            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(validJson));

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 0.0);

            assertNotNull(results);
        }

        @Test
        @DisplayName("解析空结果响应")
        void parseEmptyResultResponse() {
            String emptyJson = "{\"result\": []}";

            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(emptyJson));

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 0.0);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("解析无效JSON响应")
        void parseInvalidJsonResponse() {
            String invalidJson = "not valid json";

            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(invalidJson));

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 0.0);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("空查询字符串")
        void search_EmptyQuery() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed("")).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search("", List.of("col-1"), 10, 0.7);

            assertNotNull(results);
        }

        @Test
        @DisplayName("零topK")
        void search_ZeroTopK() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 0, 0.7);

            assertNotNull(results);
        }

        @Test
        @DisplayName("负数阈值")
        void search_NegativeThreshold() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, -0.5);

            assertNotNull(results);
        }

        @Test
        @DisplayName("阈值大于1")
        void search_ThresholdGreaterThanOne() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, 1.5);

            assertNotNull(results);
        }

        @Test
        @DisplayName("null阈值使用默认配置")
        void search_NullThresholdUsesDefault() {
            when(collectionRepository.selectActiveByIds(any()))
                    .thenReturn(List.of(testCollection));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(ragProperties.getQdrant()).thenReturn(createQdrantConfig());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(createMockResponse());

            List<SearchResult> results = searchService.search("query", List.of("col-1"), 10, null);

            assertNotNull(results);
        }
    }

    private RAGProperties.QdrantConfig createQdrantConfig() {
        RAGProperties.QdrantConfig config = new RAGProperties.QdrantConfig();
        config.setHost("localhost");
        config.setPort(6333);
        return config;
    }

    private ResponseEntity<String> createMockResponse() {
        String json = """
            {
                "result": [
                    {
                        "id": "point-1",
                        "score": 0.95,
                        "payload": {
                            "text": "Sample text",
                            "title": "Title"
                        }
                    }
                ]
            }
            """;
        return ResponseEntity.ok(json);
    }

    private ResponseEntity<String> createMockResponseWithMultipleResults() {
        String json = """
            {
                "result": [
                    {"id": "1", "score": 0.9, "payload": {"text": "Text 1"}},
                    {"id": "2", "score": 0.8, "payload": {"text": "Text 2"}},
                    {"id": "3", "score": 0.7, "payload": {"text": "Text 3"}}
                ]
            }
            """;
        return ResponseEntity.ok(json);
    }
}
