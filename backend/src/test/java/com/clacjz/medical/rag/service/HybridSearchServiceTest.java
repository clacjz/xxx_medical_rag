package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.dto.BatchAccessCheckResult;
import com.clacjz.medical.rag.model.dto.SearchResult;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchResult;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.DocumentChunk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HybridSearchService 单元测试")
class HybridSearchServiceTest {

    @Mock
    private VectorCrudService vectorCrudService;

    @Mock
    private EsIndexService esIndexService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Mock
    private DocumentChunkService documentChunkService;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private RAGProperties ragProperties;

    private HybridSearchService hybridSearchService;

    private Executor taskExecutor;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_COLLECTION_ID = "collection-123";
    private static final String TEST_DOC_ID = "doc-123";

    @BeforeEach
    void setUp() {
        taskExecutor = Executors.newSingleThreadExecutor();
        hybridSearchService = new HybridSearchService(
                vectorCrudService, esIndexService, embeddingService, collectionMapper,
                elasticsearchClient, documentPermissionService, documentChunkService,
                documentMapper, taskExecutor, ragProperties);

        // Setup default RAGProperties mock
        RAGProperties.HybridSearchConfig hybridConfig = new RAGProperties.HybridSearchConfig();
        hybridConfig.setEsOnlyScoreFactor(0.8);
        lenient().when(ragProperties.getHybridSearch()).thenReturn(hybridConfig);
    }

    @Nested
    @DisplayName("search - 混合检索测试")
    class SearchTests {

        @Test
        @DisplayName("collectionIds为空时返回空列表")
        void shouldReturnEmptyWhenCollectionIdsEmpty() {
            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", Collections.emptyList(), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("collectionIds为null时返回空列表")
        void shouldReturnEmptyWhenCollectionIdsNull() {
            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", null, 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("所有collectionIds都无效时返回空列表")
        void shouldReturnEmptyWhenAllCollectionIdsInvalid() {
            List<String> invalidIds = Arrays.asList("  ", "", null);
            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", invalidIds, 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("没有活跃知识库时返回空列表")
        void shouldReturnEmptyWhenNoActiveCollections() {
            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(Collections.emptyList());

            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("embedding返回null时返回空列表")
        void shouldReturnEmptyWhenEmbeddingNull() {
            Collection collection = createTestCollection();
            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(null);

            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("正常检索流程返回排序结果")
        void shouldReturnSortedResults() {
            Collection collection = createTestCollection();
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(queryVector);

            // Mock Qdrant search with multiple results
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("document_id", "doc-1");
            payload1.put("title", "文档1");
            payload1.put("content", "内容1");

            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("document_id", "doc-2");
            payload2.put("title", "文档2");
            payload2.put("content", "内容2");

            VectorSearchResult vectorResult1 = VectorSearchResult.builder()
                    .id("chunk-1").score(0.9).payload(payload1).build();
            VectorSearchResult vectorResult2 = VectorSearchResult.builder()
                    .id("chunk-2").score(0.7).payload(payload2).build();

            when(vectorCrudService.searchVectors(anyString(), any())).thenReturn(Arrays.asList(vectorResult1, vectorResult2));

            // Mock ES search
            mockEsSearchResponse(Collections.emptyList());

            // Mock permission check
            BatchAccessCheckResult accessResult = BatchAccessCheckResult.builder()
                    .results(Arrays.asList(
                            BatchAccessCheckResult.DocumentAccessResult.builder().documentId("doc-1").hasAccess(true).build(),
                            BatchAccessCheckResult.DocumentAccessResult.builder().documentId("doc-2").hasAccess(true).build()))
                    .build();
            when(documentPermissionService.batchCheckAccess(anyString(), anyList())).thenReturn(accessResult);

            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);

            assertNotNull(results);
            assertEquals(2, results.size());
            // Verify sorted by score descending
            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        @DisplayName("topK参数限制返回结果数量")
        void shouldLimitResultsByTopK() {
            Collection collection = createTestCollection();
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(queryVector);

            // Mock Qdrant search with 5 results
            List<VectorSearchResult> vectorResults = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("document_id", "doc-" + i);
                payload.put("title", "文档" + i);
                payload.put("content", "内容" + i);
                vectorResults.add(VectorSearchResult.builder()
                        .id("chunk-" + i).score(0.9 - i * 0.1).payload(payload).build());
            }
            when(vectorCrudService.searchVectors(anyString(), any())).thenReturn(vectorResults);
            mockEsSearchResponse(Collections.emptyList());

            BatchAccessCheckResult accessResult = BatchAccessCheckResult.builder()
                    .results(vectorResults.stream()
                            .map(vr -> BatchAccessCheckResult.DocumentAccessResult.builder()
                                    .documentId((String)vr.getPayload().get("document_id")).hasAccess(true).build())
                            .toList())
                    .build();
            when(documentPermissionService.batchCheckAccess(anyString(), anyList())).thenReturn(accessResult);

            // Request only top 2
            List<SearchResult> results = hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 2, 0.3, TEST_USER_ID);

            assertNotNull(results);
            assertEquals(2, results.size());
        }
    }

    @Nested
    @DisplayName("searchVectorOnly - 纯向量检索测试")
    class SearchVectorOnlyTests {

        @Test
        @DisplayName("collectionIds为空时返回空列表")
        void shouldReturnEmptyWhenCollectionIdsEmpty() {
            List<SearchResult> results = hybridSearchService.searchVectorOnly(
                    "测试查询", Collections.emptyList(), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("没有活跃知识库时返回空列表")
        void shouldReturnEmptyWhenNoActiveCollections() {
            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(Collections.emptyList());

            List<SearchResult> results = hybridSearchService.searchVectorOnly(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("embedding返回null时返回空列表")
        void shouldReturnEmptyWhenEmbeddingNull() {
            Collection collection = createTestCollection();
            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(null);

            List<SearchResult> results = hybridSearchService.searchVectorOnly(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("searchExactMatch - 精确匹配检索测试")
    class SearchExactMatchTests {

        @Test
        @DisplayName("collectionIds为空时返回空列表")
        void shouldReturnEmptyWhenCollectionIdsEmpty() {
            List<SearchResult> results = hybridSearchService.searchExactMatch(
                    "测试查询", Collections.emptyList(), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("没有活跃知识库时返回空列表")
        void shouldReturnEmptyWhenNoActiveCollections() {
            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(Collections.emptyList());

            List<SearchResult> results = hybridSearchService.searchExactMatch(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("search - 文档级RRF融合测试")
    class DocumentLevelRrfTests {

        /**
         * Helper: build a full search mock chain (collection, embedding, qdrant, ES, permissions)
         * and return the results of hybridSearchService.search().
         */
        private List<SearchResult> executeSearch(
                List<VectorSearchResult> qdrantResults,
                List<Map<String, Object>> esHits,
                List<String> accessibleDocIds) {
            Collection collection = createTestCollection();
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(queryVector);
            when(vectorCrudService.searchVectors(anyString(), any())).thenReturn(qdrantResults);
            mockEsSearchResponse(esHits);

            // Build permission check result
            List<BatchAccessCheckResult.DocumentAccessResult> accessEntries = accessibleDocIds.stream()
                    .map(docId -> BatchAccessCheckResult.DocumentAccessResult.builder()
                            .documentId(docId).hasAccess(true).build())
                    .toList();
            BatchAccessCheckResult accessResult = BatchAccessCheckResult.builder()
                    .results(accessEntries).build();
            when(documentPermissionService.batchCheckAccess(anyString(), anyList())).thenReturn(accessResult);

            return hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
        }

        @Test
        @DisplayName("同一文档的多个chunk聚合为一个结果，RRF分数求和")
        void shouldAggregateMultipleChunksOfSameDocument() {
            // Two chunks belonging to the same document "doc-A"
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("document_id", "doc-A");
            payload1.put("title", "文档A");
            payload1.put("content", "内容A-第1段");

            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("document_id", "doc-A");
            payload2.put("title", "文档A");
            payload2.put("content", "内容A-第2段");

            VectorSearchResult vectorResult1 = VectorSearchResult.builder()
                    .id("chunk-1").score(0.95).payload(payload1).build();
            VectorSearchResult vectorResult2 = VectorSearchResult.builder()
                    .id("chunk-2").score(0.80).payload(payload2).build();

            // No ES hits
            List<SearchResult> results = executeSearch(
                    Arrays.asList(vectorResult1, vectorResult2),
                    Collections.emptyList(),
                    Arrays.asList("doc-A"));

            // Should be aggregated into a single document-level result
            assertEquals(1, results.size());
            SearchResult aggregated = results.get(0);
            assertEquals("doc-A", aggregated.getDocumentId());

            // RRF_K = 60; chunk-1 rank=1, chunk-2 rank=2
            // Expected aggregated score = 1/(60+1) + 1/(60+2) = 1/61 + 1/62
            double expectedScore = 1.0 / (60 + 1) + 1.0 / (60 + 2);
            assertEquals(expectedScore, aggregated.getScore(), 0.0001,
                    "Aggregated RRF score should be sum of per-chunk RRF scores");

            // Representative chunk should be the first (best) chunk
            assertEquals("chunk-1", aggregated.getId());
        }

        @Test
        @DisplayName("每个文档只有一个chunk时，行为与之前相同")
        void shouldBehaveSameAsBeforeWhenSingleChunkPerDocument() {
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("document_id", "doc-X");
            payload1.put("title", "文档X");
            payload1.put("content", "内容X");

            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("document_id", "doc-Y");
            payload2.put("title", "文档Y");
            payload2.put("content", "内容Y");

            VectorSearchResult vectorResult1 = VectorSearchResult.builder()
                    .id("chunk-x1").score(0.9).payload(payload1).build();
            VectorSearchResult vectorResult2 = VectorSearchResult.builder()
                    .id("chunk-y1").score(0.7).payload(payload2).build();

            List<SearchResult> results = executeSearch(
                    Arrays.asList(vectorResult1, vectorResult2),
                    Collections.emptyList(),
                    Arrays.asList("doc-X", "doc-Y"));

            // Two distinct documents => two results
            assertEquals(2, results.size());

            // Each has a single-chunk RRF score
            double expectedScoreX = 1.0 / (60 + 1); // rank 1
            double expectedScoreY = 1.0 / (60 + 2); // rank 2
            assertEquals(expectedScoreX, results.get(0).getScore(), 0.0001);
            assertEquals(expectedScoreY, results.get(1).getScore(), 0.0001);
        }

        @Test
        @DisplayName("ES-only文档在文档级融合后仍被包含")
        void shouldIncludeEsOnlyDocumentsAfterFusion() {
            // Qdrant returns one chunk for doc-A
            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", "doc-A");
            payload.put("title", "文档A");
            payload.put("content", "内容A");

            VectorSearchResult vectorResult = VectorSearchResult.builder()
                    .id("chunk-a1").score(0.9).payload(payload).build();

            // ES returns doc-A (already in Qdrant) and doc-B (ES-only)
            Map<String, Object> esHitA = new HashMap<>();
            esHitA.put("document_id", "doc-A");
            esHitA.put("title", "文档A");
            esHitA.put("full_text", "ES全文A");

            Map<String, Object> esHitB = new HashMap<>();
            esHitB.put("document_id", "doc-B");
            esHitB.put("title", "文档B");
            esHitB.put("full_text", "ES全文B");

            // Mock ES-only doc-B chunk from MySQL
            DocumentChunk chunkB = DocumentChunk.builder()
                    .id("chunk-b1").documentId("doc-B").content("MySQL中的文档B内容").build();
            when(documentChunkService.getChunksByDocumentIds(anyList()))
                    .thenReturn(Map.of("doc-B", List.of(chunkB)));

            // Mock document title lookup
            Document docB = new Document();
            docB.setId("doc-B");
            docB.setTitle("文档B");
            when(documentMapper.selectBatchIds(anyList())).thenReturn(List.of(docB));

            when(esIndexService.getIndexName(anyString())).thenReturn("idx-test");

            List<SearchResult> results = executeSearch(
                    List.of(vectorResult),
                    Arrays.asList(esHitA, esHitB),
                    Arrays.asList("doc-A", "doc-B"));

            // Both doc-A and doc-B should appear
            assertEquals(2, results.size());
            Set<String> docIds = results.stream()
                    .map(SearchResult::getDocumentId).collect(Collectors.toSet());
            assertTrue(docIds.contains("doc-A"), "doc-A from Qdrant+ES should be present");
            assertTrue(docIds.contains("doc-B"), "doc-B ES-only should be present");

            // doc-A (Qdrant+ES RRF) should score higher than doc-B (ES-only with factor)
            SearchResult resultA = results.stream()
                    .filter(r -> "doc-A".equals(r.getDocumentId())).findFirst().orElseThrow();
            SearchResult resultB = results.stream()
                    .filter(r -> "doc-B".equals(r.getDocumentId())).findFirst().orElseThrow();
            assertTrue(resultA.getScore() > resultB.getScore(),
                    "Qdrant+ES fused doc should outscore ES-only doc");
        }

        @Test
        @DisplayName("仅在Qdrant中有chunk的文档（不在ES中）仍出现在结果中")
        void shouldIncludeDocumentsOnlyInQdrantNotInEs() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", "doc-Q");
            payload.put("title", "仅向量文档");
            payload.put("content", "仅存在于Qdrant中的内容");

            VectorSearchResult vectorResult = VectorSearchResult.builder()
                    .id("chunk-q1").score(0.85).payload(payload).build();

            // ES returns empty
            List<SearchResult> results = executeSearch(
                    List.of(vectorResult),
                    Collections.emptyList(),
                    Arrays.asList("doc-Q"));

            assertEquals(1, results.size());
            assertEquals("doc-Q", results.get(0).getDocumentId());

            // Score = pure Qdrant RRF: 1/(60+1)
            double expectedScore = 1.0 / (60 + 1);
            assertEquals(expectedScore, results.get(0).getScore(), 0.0001,
                    "Qdrant-only doc should have RRF score from vector rank alone");
        }

        @Test
        @DisplayName("文档级结果使用withScore设置聚合分数")
        void shouldUseWithScoreForAggregatedDocumentLevelResults() {
            // Two chunks for doc-A, one for doc-B
            Map<String, Object> payloadA1 = new HashMap<>();
            payloadA1.put("document_id", "doc-A");
            payloadA1.put("title", "文档A");
            payloadA1.put("content", "内容A1");
            payloadA1.put("start_page", 1);
            payloadA1.put("end_page", 3);

            Map<String, Object> payloadA2 = new HashMap<>();
            payloadA2.put("document_id", "doc-A");
            payloadA2.put("title", "文档A");
            payloadA2.put("content", "内容A2");
            payloadA2.put("start_page", 4);
            payloadA2.put("end_page", 6);

            Map<String, Object> payloadB = new HashMap<>();
            payloadB.put("document_id", "doc-B");
            payloadB.put("title", "文档B");
            payloadB.put("content", "内容B");
            payloadB.put("start_page", 1);
            payloadB.put("end_page", 2);

            VectorSearchResult vA1 = VectorSearchResult.builder()
                    .id("chunk-a1").score(0.95).payload(payloadA1).build();
            VectorSearchResult vA2 = VectorSearchResult.builder()
                    .id("chunk-a2").score(0.80).payload(payloadA2).build();
            VectorSearchResult vB = VectorSearchResult.builder()
                    .id("chunk-b1").score(0.70).payload(payloadB).build();

            // ES returns doc-A at rank 1, doc-B at rank 2
            Map<String, Object> esHitA = new HashMap<>();
            esHitA.put("document_id", "doc-A");
            esHitA.put("title", "文档A");
            esHitA.put("full_text", "ES全文A");

            Map<String, Object> esHitB = new HashMap<>();
            esHitB.put("document_id", "doc-B");
            esHitB.put("title", "文档B");
            esHitB.put("full_text", "ES全文B");

            when(esIndexService.getIndexName(anyString())).thenReturn("idx-test");

            List<SearchResult> results = executeSearch(
                    Arrays.asList(vA1, vA2, vB),
                    Arrays.asList(esHitA, esHitB),
                    Arrays.asList("doc-A", "doc-B"));

            assertEquals(2, results.size());

            // doc-A: aggregated RRF = [1/(60+1) + 1/(60+1)] + [1/(60+2)]
            // chunk-a1 rank=1, has ES rank 1 => 1/61 + 1/61
            // chunk-a2 rank=2, has ES rank 1 => 1/62 + 1/61
            // Total doc-A = 1/61 + 1/61 + 1/62 + 1/61 ... wait, let me recalculate:
            // chunk-a1 (qRank=1): rrf = 1/61 + 1/(60+1)=1/61 => 2/61
            // chunk-a2 (qRank=2): rrf = 1/62 + 1/(60+1)=1/61 => 1/62 + 1/61
            // Aggregated doc-A = 2/61 + 1/62 + 1/61 = 3/61 + 1/62
            double expectedScoreA = 3.0 / 61.0 + 1.0 / 62.0;

            // doc-B: single chunk (qRank=3), ES rank=2 => 1/63 + 1/62
            double expectedScoreB = 1.0 / (60 + 3) + 1.0 / (60 + 2);

            // doc-A should be first (higher score)
            assertEquals("doc-A", results.get(0).getDocumentId());
            assertEquals(expectedScoreA, results.get(0).getScore(), 0.0001,
                    "doc-A aggregated score should match summed RRF across chunks + ES");

            assertEquals("doc-B", results.get(1).getDocumentId());
            assertEquals(expectedScoreB, results.get(1).getScore(), 0.0001,
                    "doc-B score should be single-chunk RRF + ES contribution");

            // Verify that the representative chunk fields are preserved from the best chunk
            SearchResult docAResult = results.get(0);
            assertEquals("chunk-a1", docAResult.getId(),
                    "Representative chunk should be the first (best) chunk");
            assertEquals("内容A1", docAResult.getText());
            assertEquals(Integer.valueOf(1), docAResult.getStartPage());
            assertEquals(Integer.valueOf(3), docAResult.getEndPage());
        }
    }

    @Nested
    @DisplayName("search - 文档级RRF融合边界条件测试")
    class DocumentLevelRrfEdgeCaseTests {

        /**
         * Helper: build a full search mock chain (collection, embedding, qdrant, ES, permissions)
         * and return the results of hybridSearchService.search().
         */
        private List<SearchResult> executeSearch(
                List<VectorSearchResult> qdrantResults,
                List<Map<String, Object>> esHits,
                List<String> accessibleDocIds) {
            Collection collection = createTestCollection();
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            when(collectionMapper.selectActiveByIds(anyList())).thenReturn(List.of(collection));
            when(embeddingService.embed(anyString())).thenReturn(queryVector);
            when(vectorCrudService.searchVectors(anyString(), any())).thenReturn(qdrantResults);
            mockEsSearchResponse(esHits);

            // Build permission check result
            List<BatchAccessCheckResult.DocumentAccessResult> accessEntries = accessibleDocIds.stream()
                    .map(docId -> BatchAccessCheckResult.DocumentAccessResult.builder()
                            .documentId(docId).hasAccess(true).build())
                    .toList();
            BatchAccessCheckResult accessResult = BatchAccessCheckResult.builder()
                    .results(accessEntries).build();
            when(documentPermissionService.batchCheckAccess(anyString(), anyList())).thenReturn(accessResult);

            return hybridSearchService.search(
                    "测试查询", Arrays.asList(TEST_COLLECTION_ID), 10, 0.3, TEST_USER_ID);
        }

        @Test
        @DisplayName("Qdrant结果为空时，融合结果也为空")
        void emptyQdrantResults_noFusedResults() {
            // No Qdrant results, no ES results
            List<SearchResult> results = executeSearch(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());

            assertTrue(results.isEmpty(),
                    "Empty Qdrant results with no ES hits should produce no fused results");
        }

        @Test
        @DisplayName("ES结果为空时，仅Qdrant结果参与融合")
        void emptyEsResults_onlyQdrantResults() {
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("document_id", "doc-A");
            payload1.put("title", "文档A");
            payload1.put("content", "内容A");

            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("document_id", "doc-B");
            payload2.put("title", "文档B");
            payload2.put("content", "内容B");

            VectorSearchResult vA = VectorSearchResult.builder()
                    .id("chunk-a1").score(0.9).payload(payload1).build();
            VectorSearchResult vB = VectorSearchResult.builder()
                    .id("chunk-b1").score(0.7).payload(payload2).build();

            // ES returns empty
            List<SearchResult> results = executeSearch(
                    Arrays.asList(vA, vB),
                    Collections.emptyList(),
                    Arrays.asList("doc-A", "doc-B"));

            assertEquals(2, results.size(),
                    "Only Qdrant results should appear when ES is empty");

            // Scores should be pure Qdrant RRF (no ES contribution)
            double expectedScoreA = 1.0 / (60 + 1); // rank 1
            double expectedScoreB = 1.0 / (60 + 2); // rank 2
            assertEquals(expectedScoreA, results.get(0).getScore(), 0.0001,
                    "doc-A should have pure Qdrant RRF score");
            assertEquals(expectedScoreB, results.get(1).getScore(), 0.0001,
                    "doc-B should have pure Qdrant RRF score");
        }

        @Test
        @DisplayName("同一文档多个chunk时，最高分chunk作为代表")
        void documentWithManyChunks_highestScoredChunkAsRepresentative() {
            // Three chunks for doc-A with decreasing scores
            Map<String, Object> payload1 = new HashMap<>();
            payload1.put("document_id", "doc-A");
            payload1.put("title", "文档A");
            payload1.put("content", "第一段内容-最高分");
            payload1.put("start_page", 1);
            payload1.put("end_page", 5);

            Map<String, Object> payload2 = new HashMap<>();
            payload2.put("document_id", "doc-A");
            payload2.put("title", "文档A");
            payload2.put("content", "第二段内容-中等分");
            payload2.put("start_page", 6);
            payload2.put("end_page", 10);

            Map<String, Object> payload3 = new HashMap<>();
            payload3.put("document_id", "doc-A");
            payload3.put("title", "文档A");
            payload3.put("content", "第三段内容-最低分");
            payload3.put("start_page", 11);
            payload3.put("end_page", 15);

            VectorSearchResult v1 = VectorSearchResult.builder()
                    .id("chunk-a1").score(0.95).payload(payload1).build();
            VectorSearchResult v2 = VectorSearchResult.builder()
                    .id("chunk-a2").score(0.80).payload(payload2).build();
            VectorSearchResult v3 = VectorSearchResult.builder()
                    .id("chunk-a3").score(0.65).payload(payload3).build();

            List<SearchResult> results = executeSearch(
                    Arrays.asList(v1, v2, v3),
                    Collections.emptyList(),
                    Arrays.asList("doc-A"));

            assertEquals(1, results.size(),
                    "Three chunks of same document should aggregate into one result");

            SearchResult representative = results.get(0);
            assertEquals("doc-A", representative.getDocumentId());
            // The first chunk (rank 1, highest score) should be the representative
            assertEquals("chunk-a1", representative.getId(),
                    "Representative chunk should be the first (best) chunk");
            assertEquals("第一段内容-最高分", representative.getText(),
                    "Representative text should come from the best chunk");
            assertEquals(Integer.valueOf(1), representative.getStartPage(),
                    "Representative startPage should come from the best chunk");
            assertEquals(Integer.valueOf(5), representative.getEndPage(),
                    "Representative endPage should come from the best chunk");

            // Aggregated RRF score = 1/(60+1) + 1/(60+2) + 1/(60+3)
            double expectedScore = 1.0 / 61.0 + 1.0 / 62.0 + 1.0 / 63.0;
            assertEquals(expectedScore, representative.getScore(), 0.0001,
                    "Aggregated score should be sum of all chunk RRF scores");
        }

        @Test
        @DisplayName("同一文档在Qdrant和ES中都出现时，聚合分数高于任一单独来源")
        void sameDocumentInBothQdrantAndEs_higherAggregatedScore() {
            // doc-A appears in Qdrant
            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", "doc-A");
            payload.put("title", "文档A");
            payload.put("content", "向量检索内容");

            VectorSearchResult vA = VectorSearchResult.builder()
                    .id("chunk-a1").score(0.9).payload(payload).build();

            // doc-A also appears in ES at rank 1
            Map<String, Object> esHitA = new HashMap<>();
            esHitA.put("document_id", "doc-A");
            esHitA.put("title", "文档A");
            esHitA.put("full_text", "ES全文A");

            when(esIndexService.getIndexName(anyString())).thenReturn("idx-test");

            List<SearchResult> results = executeSearch(
                    List.of(vA),
                    Arrays.asList(esHitA),
                    Arrays.asList("doc-A"));

            assertEquals(1, results.size());
            SearchResult fused = results.get(0);

            // Qdrant-only RRF score: 1/(60+1) = 1/61
            double qdrantOnlyScore = 1.0 / (60 + 1);
            // ES-only RRF score: 1/(60+1) = 1/61
            double esOnlyScore = 1.0 / (60 + 1);
            // Combined: 1/61 + 1/61 = 2/61
            double combinedScore = qdrantOnlyScore + esOnlyScore;

            assertTrue(fused.getScore() > qdrantOnlyScore,
                    "Fused score should be higher than Qdrant-only score");
            assertTrue(fused.getScore() > esOnlyScore,
                    "Fused score should be higher than ES-only score");
            assertEquals(combinedScore, fused.getScore(), 0.0001,
                    "Fused score should equal sum of Qdrant and ES RRF contributions");
        }

        @Test
        @DisplayName("withScore保留所有字段，仅替换score值")
        void withScore_preservesAllFields() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "qdrant");
            metadata.put("chunk_index", 3);

            SearchResult original = SearchResult.builder()
                    .id("chunk-1")
                    .documentId("doc-X")
                    .collectionId("col-Y")
                    .text("原始文本内容")
                    .score(0.5)
                    .title("测试文档")
                    .snippet("原始文本内容".length() > 200
                            ? "原始文本内容".substring(0, 200) + "..."
                            : "原始文本内容")
                    .startPage(10)
                    .endPage(20)
                    .metadata(metadata)
                    .build();

            double newScore = 0.01639; // ~1/61
            SearchResult updated = original.withScore(newScore);

            // Score should be updated
            assertEquals(newScore, updated.getScore(), 0.00001,
                    "withScore should replace the score value");

            // All other fields should be preserved
            assertEquals(original.getId(), updated.getId(),
                    "withScore should preserve id");
            assertEquals(original.getDocumentId(), updated.getDocumentId(),
                    "withScore should preserve documentId");
            assertEquals(original.getCollectionId(), updated.getCollectionId(),
                    "withScore should preserve collectionId");
            assertEquals(original.getText(), updated.getText(),
                    "withScore should preserve text");
            assertEquals(original.getTitle(), updated.getTitle(),
                    "withScore should preserve title");
            assertEquals(original.getSnippet(), updated.getSnippet(),
                    "withScore should preserve snippet");
            assertEquals(original.getStartPage(), updated.getStartPage(),
                    "withScore should preserve startPage");
            assertEquals(original.getEndPage(), updated.getEndPage(),
                    "withScore should preserve endPage");
            assertEquals(original.getMetadata(), updated.getMetadata(),
                    "withScore should preserve metadata");

            // Original should be unchanged (immutable copy)
            assertNotEquals(original.getScore(), updated.getScore(),
                    "Original score should remain unchanged after withScore");
        }
    }

    private Collection createTestCollection() {
        Collection collection = new Collection();
        collection.setId(TEST_COLLECTION_ID);
        collection.setName("测试知识库");
        collection.setDepartmentId("dept-123");
        collection.setStatus(CollectionStatus.ACTIVE);

        CollectionConfig config = new CollectionConfig();
        CollectionConfig.RetrievalConfig retrievalConfig = new CollectionConfig.RetrievalConfig();
        retrievalConfig.setSimilarityThreshold(0.3);
        config.setRetrieval(retrievalConfig);
        collection.setConfig(config);

        return collection;
    }

    @SuppressWarnings("unchecked")
    private void mockEsSearchResponse(List<Map<String, Object>> hits) {
        try {
            SearchResponse<Map> mockResponse = mock(SearchResponse.class);
            HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
            when(mockResponse.hits()).thenReturn(hitsMetadata);

            List<Hit<Map>> hitList = new ArrayList<>();
            for (Map<String, Object> hitData : hits) {
                Hit<Map> hit = mock(Hit.class);
                when(hit.source()).thenReturn(hitData);
                when(hit.score()).thenReturn(1.0);
                hitList.add(hit);
            }
            when(hitsMetadata.hits()).thenReturn(hitList);

            when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                    .thenReturn(mockResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
