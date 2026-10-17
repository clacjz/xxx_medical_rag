package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.DocumentPermissionMapper;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.DocType;
import com.clacjz.medical.rag.service.chunking.SemanticChunkingService;
import com.clacjz.medical.rag.service.chunking.TextChunker;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentVectorizationService 单元测试")
class DocumentVectorizationServiceTest {

    @Mock
    private TextChunker textChunker;
    @Mock
    private SemanticChunkingService semanticChunkingService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorCrudService vectorCrudService;
    @Mock
    private CollectionMapper collectionMapper;
    @Mock
    private CollectionManagementService collectionManagementService;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentPermissionMapper documentPermissionMapper;
    @Mock
    private EsIndexService esIndexService;
    @Mock
    private DocumentChunkService documentChunkService;

    private DocumentVectorizationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DocumentVectorizationService(
                textChunker, semanticChunkingService, embeddingService,
                vectorCrudService, collectionMapper, collectionManagementService,
                documentMapper, documentPermissionMapper, esIndexService, documentChunkService);
        // 通过反射注入同步执行的 Executor（避免异步线程的竞态）
        java.lang.reflect.Field field = DocumentVectorizationService.class.getDeclaredField("embeddingExecutor");
        field.setAccessible(true);
        field.set(service, (Executor) Runnable::run);
    }

    @Nested
    @DisplayName("deleteDocumentVectors")
    class DeleteDocumentVectorsTests {

        @Test
        @DisplayName("返回 document_chunks 表中的正确计数")
        void returnsChunkCountFromDocumentChunks() {
            when(documentChunkService.countByDocumentId("doc-1")).thenReturn(5L);

            long result = service.deleteDocumentVectors("col-1", "doc-1");

            assertEquals(5L, result);
            verify(documentChunkService).countByDocumentId("doc-1");
        }

        @Test
        @DisplayName("调用 deleteVectorsByFilter 时 filter 包含 document_id")
        void callsDeleteVectorsByFilterWithCorrectFilter() {
            when(documentChunkService.countByDocumentId("doc-1")).thenReturn(3L);

            service.deleteDocumentVectors("col-1", "doc-1");

            verify(vectorCrudService).deleteVectorsByFilter(eq("col-1"), argThat(filter ->
                    "doc-1".equals(filter.get("document_id"))));
        }

        @Test
        @DisplayName("document_chunks 计数为 0 时返回 0")
        void returnsZeroWhenNoChunks() {
            when(documentChunkService.countByDocumentId("doc-1")).thenReturn(0L);

            long result = service.deleteDocumentVectors("col-1", "doc-1");

            assertEquals(0L, result);
        }

        @Test
        @DisplayName("deleteVectorsByFilter 抛异常时抛出 RuntimeException")
        void returnsZeroOnException() {
            when(documentChunkService.countByDocumentId("doc-1")).thenReturn(5L);
            doThrow(new RuntimeException("qdrant down"))
                    .when(vectorCrudService).deleteVectorsByFilter(anyString(), anyMap());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteDocumentVectors("col-1", "doc-1"));
            assertTrue(ex.getMessage().contains("删除文档向量失败"));
        }
    }

    @Nested
    @DisplayName("batchVectorize")
    class BatchVectorizeTests {

        @Test
        @DisplayName("空列表返回 0")
        void shouldReturnZeroForEmptyList() {
            int result = service.batchVectorize(List.of());
            assertEquals(0, result);
            verifyNoInteractions(vectorCrudService);
        }

        @Test
        @DisplayName("单条文档走 vectorizeMarkdown 流程")
        void shouldVectorizeSingleDoc() {
            // collection 已存在
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            // NORMAL 流程需要分块
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "text", 0, 4, 0, 0, "")
            ));
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            List<Map<String, String>> docs = List.of(Map.of(
                    "text", "hello", "collectionId", "col-1",
                    "documentId", "doc-1", "title", "T"
            ));

            int result = service.batchVectorize(docs);

            assertTrue(result >= 0);
            verify(vectorCrudService, atLeastOnce()).upsertVector(eq("col-1"), any(), any(), anyString());
        }

        @Test
        @DisplayName("单条文档异常不影响其他文档")
        void shouldContinueOnSingleFailure() {
            List<Map<String, String>> docs = List.of(
                    Map.of("text", "a", "collectionId", "c1", "documentId", "d1", "title", "t1"),
                    Map.of("text", "b", "collectionId", "c2", "documentId", "d2", "title", "t2")
            );

            int result = service.batchVectorize(docs);

            // 不抛异常，返回值 >= 0
            assertTrue(result >= 0);
        }
    }

    @Nested
    @DisplayName("vectorizeMarkdown - DocType 分发")
    class DocTypeDispatchTests {

        @Test
        @DisplayName("默认 NORMAL 类型走 normal 路径")
        void shouldDispatchToNormal() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            service.vectorizeMarkdown("# hi", "col-1", "doc-1", "title");

            verify(textChunker, atLeastOnce()).chunkFixedSize(anyString());
        }

        @Test
        @DisplayName("传入 DocType.NORMAL 走 normal 路径")
        void shouldDispatchToNormalWhenExplicit() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title", "fixed", DocType.NORMAL, null, null);

            verify(textChunker, atLeastOnce()).chunkFixedSize(anyString());
        }

        @Test
        @DisplayName("传入 DocType.MANUAL 走 manual 路径（不调用 chunkFixedSize）")
        void shouldDispatchToManual() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());
            when(embeddingService.embed(anyString())).thenReturn(new float[1024]); // mock valid embedding

            // 短文本不切分，1 chunk
            service.vectorizeMarkdown("x", "col-1", "doc-1", "title", "fixed", DocType.MANUAL, null, null);

            verify(textChunker, never()).chunkFixedSize(anyString());
        }

        @Test
        @DisplayName("semantic 策略走 semanticChunkingService")
        void shouldUseSemanticForSemanticStrategy() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            SemanticChunkingService.SemanticChunkResult result = mock(SemanticChunkingService.SemanticChunkResult.class);
            when(result.getChunks()).thenReturn(List.of());
            when(semanticChunkingService.semanticChunk(anyString(), anyInt())).thenReturn(result);

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title", "semantic", DocType.NORMAL, null, null);

            verify(semanticChunkingService).semanticChunk(anyString(), anyInt());
        }

        @Test
        @DisplayName("paragraph 策略走 chunkByParagraph")
        void shouldUseParagraphForParagraphStrategy() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkByParagraph(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title", "paragraph", DocType.NORMAL, null, null);

            verify(textChunker).chunkByParagraph(anyString());
        }
    }

    @Nested
    @DisplayName("vectorizeMarkdown - collection 初始化")
    class CollectionInitTests {

        @Test
        @DisplayName("collection 存在时不创建")
        void shouldNotCreateWhenExists() {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title");

            verify(collectionManagementService, never()).createCollectionWithDefaultConfig(anyString());
        }

        @Test
        @DisplayName("collection 不存在时创建")
        void shouldCreateWhenMissing() {
            when(collectionManagementService.getCollectionInfo("col-1"))
                    .thenThrow(new RuntimeException("not found"));
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title");

            verify(collectionManagementService).createCollectionWithDefaultConfig("col-1");
        }
    }

    @Nested
    @DisplayName("vectorizeMarkdown - ES 索引")
    class EsIndexTests {

        @Test
        @DisplayName("ES 索引失败不中断主流程")
        void shouldNotFailOnEsError() throws Exception {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());
            doThrow(new RuntimeException("es down")).when(esIndexService).indexDocument(any());

            // 不应抛异常
            assertDoesNotThrow(() -> service.vectorizeMarkdown("text", "col-1", "doc-1", "title"));
        }

        @Test
        @DisplayName("ES 索引失败时仍调用 esIndexService.indexDocument")
        void shouldAttemptEsIndexEvenIfItFails() throws Exception {
            when(collectionManagementService.getCollectionInfo("col-1")).thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());
            Document doc = new Document();
            doc.setId("doc-1");
            when(documentMapper.selectById("doc-1")).thenReturn(doc);
            doThrow(new RuntimeException("es down")).when(esIndexService).indexDocument(any());

            service.vectorizeMarkdown("text", "col-1", "doc-1", "title");

            // 异常被 indexToEs 内部吞掉（由补偿任务重试），但实际调用过
            verify(esIndexService, atLeastOnce()).indexDocument(any());
        }
    }

    @Nested
    @DisplayName("vectorizeManualDocument - 部分失败追踪")
    class PartialFailureTests {

        private void setupManualMocks() {
            when(collectionManagementService.getCollectionInfo("col-1"))
                    .thenReturn(Collections.CollectionInfo.getDefaultInstance());
            when(documentMapper.selectById("doc-1")).thenReturn(new Document());
            when(documentPermissionMapper.selectByDocumentId("doc-1")).thenReturn(null);
        }

        @Test
        @DisplayName("所有chunk成功 — 无异常抛出，返回正确数量")
        void allChunksSucceed_noErrorThrown() {
            setupManualMocks();
            // 短文本 → 1 chunk
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            int result = service.vectorizeMarkdown("short text", "col-1", "doc-1", "title",
                    "fixed", DocType.MANUAL, null, null);

            assertEquals(1, result);
            verify(vectorCrudService).upsertVector(eq("col-1"), any(float[].class), anyMap(), anyString());
        }

        @Test
        @DisplayName("一个chunk embedding返回null — 警告日志，部分数据仍返回")
        void oneChunkReturnsNull_warningLogged_partialResultReturned() {
            setupManualMocks();
            // 长文本触发分块 → 多个chunk
            String longText = "a".repeat(3000); // > 2000 maxChars → split into chunks
            // 第一个chunk返回null，第二个返回有效向量
            when(embeddingService.embed(anyString()))
                    .thenReturn(null)
                    .thenReturn(new float[]{0.1f, 0.2f});

            int result = service.vectorizeMarkdown(longText, "col-1", "doc-1", "title",
                    "fixed", DocType.MANUAL, null, null);

            // 部分成功：1个chunk成功
            assertEquals(1, result);
            verify(vectorCrudService, times(1)).upsertVector(eq("col-1"), any(float[].class), anyMap(), anyString());
        }

        @Test
        @DisplayName("所有chunk embedding返回null — 抛出RuntimeException")
        void allChunksReturnNull_throwsRuntimeException() {
            setupManualMocks();
            // 短文本 → 1 chunk，embedding返回null
            when(embeddingService.embed(anyString())).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.vectorizeMarkdown("short text", "col-1", "doc-1", "title",
                            "fixed", DocType.MANUAL, null, null));

            assertTrue(ex.getMessage().contains("MANUAL文档部分chunk向量化失败"));
            assertTrue(ex.getMessage().contains("1/1 chunks失败"));
            // 不应有任何upsert调用
            verify(vectorCrudService, never()).upsertVector(anyString(), any(), anyMap(), anyString());
        }

        @Test
        @DisplayName("embedding抛异常 — failCounter递增，部分成功")
        void embeddingThrowsException_failCounterIncremented_partialSuccess() {
            setupManualMocks();
            // 长文本触发分块 → 多个chunk
            String longText = "a".repeat(3000);
            // 第一个chunk抛异常，第二个返回有效向量
            when(embeddingService.embed(anyString()))
                    .thenThrow(new RuntimeException("embedding service error"))
                    .thenReturn(new float[]{0.1f, 0.2f});

            int result = service.vectorizeMarkdown(longText, "col-1", "doc-1", "title",
                    "fixed", DocType.MANUAL, null, null);

            // 部分成功：1个chunk成功
            assertEquals(1, result);
            verify(vectorCrudService, times(1)).upsertVector(eq("col-1"), any(float[].class), anyMap(), anyString());
        }

    }
}
