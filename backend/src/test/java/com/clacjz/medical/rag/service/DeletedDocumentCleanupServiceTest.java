package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeletedDocumentCleanupService 单元测试")
class DeletedDocumentCleanupServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private CollectionDocumentMapper collectionDocumentMapper;

    @Mock
    private VectorCrudService vectorCrudService;

    @Mock
    private EsIndexService esIndexService;

    @Mock
    private TempFileCleanupService tempFileCleanupService;

    @Mock
    private QdrantClient qdrantClient;

    @InjectMocks
    private DeletedDocumentCleanupService deletedDocumentCleanupService;

    private static final String TEST_DOC_ID = "doc-123";
    private static final String TEST_COLLECTION_ID = "collection-123";

    @Nested
    @DisplayName("cleanupDeletedDocuments - 清理已删除文档")
    class CleanupDeletedDocumentsTests {

        @Test
        @DisplayName("无删除文档时不执行清理")
        void shouldNotCleanupWhenNoDeletedDocs() {
            when(documentMapper.selectDeletedDocuments()).thenReturn(Collections.emptyList());

            deletedDocumentCleanupService.cleanupDeletedDocuments();

            verify(vectorCrudService, never()).deleteByDocumentId(anyString(), anyString());
            verify(documentMapper, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("7天内删除的文档跳过清理")
        void shouldSkipDocumentsDeletedWithin7Days() {
            Document recentDeleted = new Document();
            recentDeleted.setId(TEST_DOC_ID);
            recentDeleted.setUpdatedAt(LocalDateTime.now().minusDays(3));

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(recentDeleted));

            deletedDocumentCleanupService.cleanupDeletedDocuments();

            verify(vectorCrudService, never()).deleteByDocumentId(anyString(), anyString());
            verify(documentMapper, never()).deleteById(anyString());
        }
    }

    @Nested
    @DisplayName("cleanupOrphanVectors - 清理孤立向量")
    class CleanupOrphanVectorsTests {

        @Test
        @DisplayName("无活跃知识库时不执行")
        void shouldNotCleanupWhenNoActiveCollections() {
            when(collectionMapper.selectAllActive()).thenReturn(Collections.emptyList());

            deletedDocumentCleanupService.cleanupOrphanVectors();

            verify(vectorCrudService, never()).getCollectionPointCount(anyString());
        }

        @Test
        @DisplayName("无向量的知识库跳过")
        void shouldSkipCollectionWithNoVectors() {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setName("test");

            when(collectionMapper.selectAllActive()).thenReturn(List.of(collection));
            when(vectorCrudService.getCollectionPointCount("test")).thenReturn(0L);

            deletedDocumentCleanupService.cleanupOrphanVectors();

            verify(vectorCrudService, never()).scrollAllPayloadValues(anyString(), anyString());
        }

        @Test
        @DisplayName("清理孤立向量")
        void shouldDeleteOrphanVectors() {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setName("test");

            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId("valid-doc");

            when(collectionMapper.selectAllActive()).thenReturn(List.of(collection));
            when(vectorCrudService.getCollectionPointCount("test")).thenReturn(100L);
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));
            when(vectorCrudService.scrollAllPayloadValues("test", "document_id"))
                    .thenReturn(Set.of("valid-doc", "orphan-doc"));

            deletedDocumentCleanupService.cleanupOrphanVectors();

            verify(vectorCrudService).deleteByDocumentId("test", "orphan-doc");
            verify(vectorCrudService, never()).deleteByDocumentId("test", "valid-doc");
        }

        @Test
        @DisplayName("无孤立向量时不删除")
        void shouldNotDeleteWhenNoOrphanVectors() {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setName("test");

            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId("valid-doc");

            when(collectionMapper.selectAllActive()).thenReturn(List.of(collection));
            when(vectorCrudService.getCollectionPointCount("test")).thenReturn(100L);
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));
            when(vectorCrudService.scrollAllPayloadValues("test", "document_id"))
                    .thenReturn(Set.of("valid-doc"));

            deletedDocumentCleanupService.cleanupOrphanVectors();

            verify(vectorCrudService, never()).deleteByDocumentId(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("ExternalResourceCleanup - 外部资源清理测试")
    class ExternalResourceCleanupTests {

        private Document createDeletedDocument(String docId, LocalDateTime deletedAt) {
            Document doc = new Document();
            doc.setId(docId);
            doc.setUpdatedAt(deletedAt);
            return doc;
        }

        private CollectionDocument createCollectionDocument(String collectionId, String docId) {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(collectionId);
            cd.setDocumentId(docId);
            return cd;
        }

        @Test
        @DisplayName("清理时删除 Qdrant 向量")
        void cleanup_DeletesQdrantVectors() throws Exception {
            // Given: 一个超过7天的已删除文档，关联一个知识库
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            CollectionDocument cd = createCollectionDocument(TEST_COLLECTION_ID, TEST_DOC_ID);

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then: 应调用 vectorCrudService.deleteByDocumentId
            verify(vectorCrudService).deleteByDocumentId(TEST_COLLECTION_ID, TEST_DOC_ID);
        }

        @Test
        @DisplayName("清理时删除 ES 索引文档")
        void cleanup_DeletesEsIndex() throws Exception {
            // Given: 一个超过7天的已删除文档，关联一个知识库
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            CollectionDocument cd = createCollectionDocument(TEST_COLLECTION_ID, TEST_DOC_ID);

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then: 应调用 esIndexService.deleteDocument
            verify(esIndexService).deleteDocument(TEST_COLLECTION_ID, TEST_DOC_ID);
        }

        @Test
        @DisplayName("Qdrant 删除失败不阻塞 ES 清理")
        void cleanup_QdrantFailure_DoesNotBlockEsCleanup() throws Exception {
            // Given: Qdrant 删除抛出异常，但 ES 应继续清理
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            CollectionDocument cd = createCollectionDocument(TEST_COLLECTION_ID, TEST_DOC_ID);

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));
            // Qdrant 删除失败
            doThrow(new RuntimeException("Qdrant connection failed"))
                    .when(vectorCrudService).deleteByDocumentId(anyString(), anyString());

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then: ES 清理仍应被调用
            verify(esIndexService).deleteDocument(TEST_COLLECTION_ID, TEST_DOC_ID);
            // 文档仍应被物理删除（因为异常在内部 try-catch 中被捕获）
            verify(documentMapper).deleteById(TEST_DOC_ID);
        }

        @Test
        @DisplayName("ES 删除失败不阻塞 Qdrant 清理")
        void cleanup_EsFailure_DoesNotBlockQdrantCleanup() throws Exception {
            // Given: ES 删除抛出异常，但 Qdrant 应继续清理
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            CollectionDocument cd = createCollectionDocument(TEST_COLLECTION_ID, TEST_DOC_ID);

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd));
            // ES 删除失败
            doThrow(new java.io.IOException("ES connection failed"))
                    .when(esIndexService).deleteDocument(anyString(), anyString());

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then: Qdrant 清理仍应被调用
            verify(vectorCrudService).deleteByDocumentId(TEST_COLLECTION_ID, TEST_DOC_ID);
            // 文档仍应被物理删除
            verify(documentMapper).deleteById(TEST_DOC_ID);
        }

        @Test
        @DisplayName("清理时物理删除文档记录")
        void cleanup_PhysicallyDeletesDocument() {
            // Given
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then
            verify(documentMapper).deleteById(TEST_DOC_ID);
        }

        @Test
        @DisplayName("清理时删除临时文件")
        void cleanup_DeletesTempFile() {
            // Given: 文档有 processResultFile
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            doc.setProcessResultFile("/tmp/doc-result.json");

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then
            verify(tempFileCleanupService).cleanupFile("/tmp/doc-result.json");
        }

        @Test
        @DisplayName("文档无 processResultFile 时不调用临时文件清理")
        void cleanup_SkipsTempFile_whenNoProcessResultFile() {
            // Given: 文档没有 processResultFile
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            // processResultFile 默认为 null

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then
            verify(tempFileCleanupService, never()).cleanupFile(anyString());
        }

        @Test
        @DisplayName("多知识库关联时分别清理各知识库的向量")
        void cleanup_DeletesVectorsFromMultipleCollections() throws Exception {
            // Given: 一个文档关联两个知识库
            Document doc = createDeletedDocument(TEST_DOC_ID, LocalDateTime.now().minusDays(10));
            CollectionDocument cd1 = createCollectionDocument("col-1", TEST_DOC_ID);
            CollectionDocument cd2 = createCollectionDocument("col-2", TEST_DOC_ID);

            when(documentMapper.selectDeletedDocuments()).thenReturn(List.of(doc));
            when(collectionDocumentMapper.selectList(any())).thenReturn(List.of(cd1, cd2));

            // When
            deletedDocumentCleanupService.cleanupDeletedDocuments();

            // Then: 两个知识库的向量都应被删除
            verify(vectorCrudService).deleteByDocumentId("col-1", TEST_DOC_ID);
            verify(vectorCrudService).deleteByDocumentId("col-2", TEST_DOC_ID);
            try {
                verify(esIndexService).deleteDocument("col-1", TEST_DOC_ID);
                verify(esIndexService).deleteDocument("col-2", TEST_DOC_ID);
            } catch (java.io.IOException e) {
                fail("Mock should not throw IOException", e);
            }
        }
    }
}
