package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionBatchService 单元测试")
class CollectionBatchServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private CollectionDocumentMapper collectionDocumentMapper;

    @Mock
    private MinioClient minioClient;

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @InjectMocks
    private CollectionBatchService collectionBatchService;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_COLLECTION_ID = "collection-123";
    private static final String TEST_DOCUMENT_ID = "doc-123";
    private static final String TEST_DEPT_ID = "dept-123";

    @BeforeEach
    void setUp() {
        TenantContext.setDepartmentId(TEST_DEPT_ID);
        // 让TransactionTemplate.executeWithoutResult实际执行callback
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("uploadAndCreateDocument - 文件上传测试")
    class UploadAndCreateDocumentTests {

        @Test
        @DisplayName("文件名为空时返回失败")
        void shouldFailWhenFilenameEmpty() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "", "text/plain", new byte[]{});

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, emptyFile, TEST_USER_ID, "描述", "READ", null);

            assertFalse(result.isSuccess());
            assertEquals("文件名不能为空", result.getErrorMessage());
        }

        @Test
        @DisplayName("文件名只有空白时返回失败")
        void shouldFailWhenFilenameBlank() {
            MockMultipartFile blankFile = new MockMultipartFile(
                    "file", "   ", "text/plain", new byte[]{});

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, blankFile, TEST_USER_ID, "描述", "READ", null);

            assertFalse(result.isSuccess());
            assertEquals("文件名不能为空", result.getErrorMessage());
        }

        @Test
        @DisplayName("不支持的文件类型返回失败")
        void shouldFailWhenUnsupportedFileType() {
            RAGProperties.DocumentConfig docConfig = new RAGProperties.DocumentConfig();
            docConfig.setSupportedTypes(new String[]{"pdf", "docx"});
            when(ragProperties.getDocument()).thenReturn(docConfig);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.exe", "application/octet-stream", new byte[]{1, 2, 3});

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, file, TEST_USER_ID, "描述", "READ", null);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("不支持的文件类型"));
        }

        @Test
        @DisplayName("文件超过100MB返回失败")
        void shouldFailWhenFileSizeExceedsLimit() {
            RAGProperties.DocumentConfig docConfig = new RAGProperties.DocumentConfig();
            docConfig.setSupportedTypes(new String[]{"pdf", "txt"});
            when(ragProperties.getDocument()).thenReturn(docConfig);

            // 101MB file
            byte[] largeContent = new byte[101 * 1024 * 1024];
            MockMultipartFile file = new MockMultipartFile(
                    "file", "large.pdf", "application/pdf", largeContent);

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, file, TEST_USER_ID, "描述", "READ", null);

            assertFalse(result.isSuccess());
            assertEquals("文件大小不能超过100MB", result.getErrorMessage());
        }

        @Test
        @DisplayName("正常上传流程成功")
        void shouldSucceedWhenNormalFlow() throws Exception {
            RAGProperties.DocumentConfig docConfig = new RAGProperties.DocumentConfig();
            docConfig.setSupportedTypes(new String[]{"pdf", "txt"});
            when(ragProperties.getDocument()).thenReturn(docConfig);

            RAGProperties.MinIOConfig minioConfig = new RAGProperties.MinIOConfig();
            RAGProperties.MinIOConfig.BucketConfig bucketConfig = new RAGProperties.MinIOConfig.BucketConfig();
            bucketConfig.setDocuments("documents");
            minioConfig.setBucket(bucketConfig);
            when(ragProperties.getMinio()).thenReturn(minioConfig);

            when(minioClient.bucketExists(any())).thenReturn(false);

            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setDepartmentId(TEST_DEPT_ID);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, file, TEST_USER_ID, "描述", "READ", null);

            assertTrue(result.isSuccess());
            assertNotNull(result.getDocumentId());
            assertEquals("test.pdf", result.getFileName());
            verify(documentMapper).insert(any(Document.class));
            verify(collectionDocumentMapper).insert(any(CollectionDocument.class));
        }

        @Test
        @DisplayName("Minio bucket已存在时不抛异常")
        void shouldNotThrowWhenBucketExists() throws Exception {
            RAGProperties.DocumentConfig docConfig = new RAGProperties.DocumentConfig();
            docConfig.setSupportedTypes(new String[]{"pdf", "txt"});
            when(ragProperties.getDocument()).thenReturn(docConfig);

            RAGProperties.MinIOConfig minioConfig = new RAGProperties.MinIOConfig();
            RAGProperties.MinIOConfig.BucketConfig bucketConfig = new RAGProperties.MinIOConfig.BucketConfig();
            bucketConfig.setDocuments("documents");
            minioConfig.setBucket(bucketConfig);
            when(ragProperties.getMinio()).thenReturn(minioConfig);

            when(minioClient.bucketExists(any())).thenReturn(true);

            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setDepartmentId(TEST_DEPT_ID);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            CollectionBatchService.BatchUploadResult result =
                    collectionBatchService.uploadAndCreateDocument(
                            TEST_COLLECTION_ID, file, TEST_USER_ID, "描述", "READ", null);

            assertTrue(result.isSuccess());
            verify(minioClient, never()).makeBucket(any());
        }
    }

    @Nested
    @DisplayName("processDocumentAsync - 异步文档处理测试")
    class ProcessDocumentAsyncTests {

        @Test
        @DisplayName("知识库不存在时返回失败")
        void shouldFailWhenCollectionNotFound() throws Exception {
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(null);

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            CollectionBatchService.BatchUploadResult result = future.get();
            assertFalse(result.isSuccess());
            assertEquals("知识库不存在", result.getErrorMessage());
        }

        @Test
        @DisplayName("文档不存在时返回失败")
        void shouldFailWhenDocumentNotFound() throws Exception {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(null);

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            CollectionBatchService.BatchUploadResult result = future.get();
            assertFalse(result.isSuccess());
            assertEquals("文档不存在", result.getErrorMessage());
        }

        @Test
        @DisplayName("处理成功时返回正确结果")
        void shouldSucceedWhenProcessingSucceeds() throws Exception {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setConfig(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(10);
            processResult.setCharCount(5000);

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            CollectionBatchService.BatchUploadResult result = future.get();

            assertTrue(result.isSuccess());
            assertEquals(10, result.getChunkCount());
            assertEquals(5000, result.getCharCount());
        }

        @Test
        @DisplayName("处理失败时返回错误信息")
        void shouldReturnErrorMessageWhenProcessingFails() throws Exception {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setConfig(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(false);
            processResult.setErrorMessage("处理失败：文件损坏");

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            CollectionBatchService.BatchUploadResult result = future.get();

            assertFalse(result.isSuccess());
            assertEquals("处理失败：文件损坏", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("updateCollectionDocumentStatus - 状态更新测试")
    class UpdateStatusTests {

        @Test
        @DisplayName("更新为完成状态时设置processedAt")
        void shouldSetProcessedAtWhenCompleted() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(new Document());

            collectionBatchService.updateCollectionDocumentStatus(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "COMPLETED", null);

            assertEquals("COMPLETED", cd.getProcessingStatus());
            assertNotNull(cd.getProcessedAt());
            verify(collectionDocumentMapper).updateById(cd);
        }

        @Test
        @DisplayName("更新为失败状态时设置错误信息")
        void shouldSetErrorMessageWhenFailed() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(new Document());

            collectionBatchService.updateCollectionDocumentStatus(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "FAILED", "处理失败原因");

            assertEquals("FAILED", cd.getProcessingStatus());
            assertEquals("处理失败原因", cd.getProcessingError());
            assertNotNull(cd.getProcessedAt());
        }

        @Test
        @DisplayName("关联文档不存在时不抛异常")
        void shouldNotThrowWhenDocumentNotFound() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(null);

            assertDoesNotThrow(() ->
                    collectionBatchService.updateCollectionDocumentStatus(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "PROCESSING", null));
        }

        @Test
        @DisplayName("CollectionDocument不存在时不抛异常")
        void shouldNotThrowWhenCollectionDocumentNotFound() {
            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(null);

            assertDoesNotThrow(() ->
                    collectionBatchService.updateCollectionDocumentStatus(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "PROCESSING", null));
        }
    }

    @Nested
    @DisplayName("updateCollectionDocumentStatus - 直接测试（TransactionTemplate回调内行为）")
    class UpdateCollectionDocumentStatusDirectTests {

        @Test
        @DisplayName("COMPLETED状态时同时更新CollectionDocument和Document")
        void completedStatus_UpdatesCollectionDocumentAndDocument() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setParseStatus("pending");
            document.setChunkStatus("pending");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            collectionBatchService.updateCollectionDocumentStatus(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "COMPLETED", null);

            // CollectionDocument updated
            assertEquals("COMPLETED", cd.getProcessingStatus());
            assertNotNull(cd.getProcessedAt());
            verify(collectionDocumentMapper).updateById(cd);

            // Document updated
            assertEquals("completed", document.getParseStatus());
            assertEquals("completed", document.getChunkStatus());
            verify(documentMapper).updateById(document);
        }

        @Test
        @DisplayName("FAILED状态时同时更新CollectionDocument和Document的错误信息")
        void failedStatus_UpdatesWithError() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setParseStatus("pending");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            collectionBatchService.updateCollectionDocumentStatus(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "FAILED", "PDF解析异常");

            // CollectionDocument updated
            assertEquals("FAILED", cd.getProcessingStatus());
            assertEquals("PDF解析异常", cd.getProcessingError());
            assertNotNull(cd.getProcessedAt());
            verify(collectionDocumentMapper).updateById(cd);

            // Document updated
            assertEquals("failed", document.getParseStatus());
            assertEquals("PDF解析异常", document.getParseErrorMessage());
            verify(documentMapper).updateById(document);
        }

        @Test
        @DisplayName("PROCESSING状态时设置Document的parseStatus为processing")
        void processingStatus_UpdatesParseStatus() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setParseStatus("pending");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            collectionBatchService.updateCollectionDocumentStatus(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "PROCESSING", null);

            // CollectionDocument: status updated, no processedAt for PROCESSING
            assertEquals("PROCESSING", cd.getProcessingStatus());
            assertNull(cd.getProcessedAt());
            verify(collectionDocumentMapper).updateById(cd);

            // Document: parseStatus set to "processing"
            assertEquals("processing", document.getParseStatus());
            verify(documentMapper).updateById(document);
        }

        @Test
        @DisplayName("CollectionDocument不存在时仅更新Document，不抛异常")
        void collectionDocumentNotFound_DoesNotThrow() {
            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setParseStatus("pending");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(null);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);

            assertDoesNotThrow(() ->
                    collectionBatchService.updateCollectionDocumentStatus(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "COMPLETED", null));

            // Only Document updated
            assertEquals("completed", document.getParseStatus());
            assertEquals("completed", document.getChunkStatus());
            verify(documentMapper).updateById(document);
            verify(collectionDocumentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("Document不存在时仅更新CollectionDocument，不抛异常")
        void documentNotFound_DoesNotThrow() {
            CollectionDocument cd = new CollectionDocument();
            cd.setCollectionId(TEST_COLLECTION_ID);
            cd.setDocumentId(TEST_DOCUMENT_ID);
            cd.setProcessingStatus("PENDING");

            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(cd);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(null);

            assertDoesNotThrow(() ->
                    collectionBatchService.updateCollectionDocumentStatus(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "COMPLETED", null));

            // Only CollectionDocument updated
            assertEquals("COMPLETED", cd.getProcessingStatus());
            assertNotNull(cd.getProcessedAt());
            verify(collectionDocumentMapper).updateById(cd);
            verify(documentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("两个实体都不存在时不抛异常，不执行任何更新")
        void bothNotFound_DoesNotThrow() {
            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(
                    TEST_COLLECTION_ID, TEST_DOCUMENT_ID)).thenReturn(null);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(null);

            assertDoesNotThrow(() ->
                    collectionBatchService.updateCollectionDocumentStatus(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, "PROCESSING", null));

            verify(collectionDocumentMapper, never()).updateById(any());
            verify(documentMapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("extractChunkStrategy - 分块策略提取测试")
    class ExtractChunkStrategyTests {

        @Test
        @DisplayName("config为null时返回默认值fixed")
        void shouldReturnFixedWhenConfigNull() throws Exception {
            java.lang.reflect.Method method = CollectionBatchService.class.getDeclaredMethod(
                    "extractChunkStrategy", Object.class);
            method.setAccessible(true);

            String result = (String) method.invoke(collectionBatchService, new Object[]{null});
            assertEquals("fixed", result);
        }
    }

    @Nested
    @DisplayName("processDocumentAsync - 异步结果传播测试")
    class AsyncResultPropagationTests {

        private void setupCollectionAndDocument() {
            Collection collection = new Collection();
            collection.setId(TEST_COLLECTION_ID);
            collection.setConfig(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(collection);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);
        }

        @Test
        @DisplayName("处理成功时future.get().isSuccess()返回true")
        void futureCompletesWithCorrectResult() throws Exception {
            setupCollectionAndDocument();

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(5);
            processResult.setCharCount(2000);

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertTrue(future.get().isSuccess());
        }

        @Test
        @DisplayName("处理成功时future.get().getChunkCount()与processResult一致")
        void futureCompletesWithCorrectChunkCount() throws Exception {
            setupCollectionAndDocument();

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(42);
            processResult.setCharCount(10000);

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertEquals(42, future.get().getChunkCount());
        }

        @Test
        @DisplayName("处理成功时future.get().getCharCount()与processResult一致")
        void futureCompletesWithCorrectCharCount() throws Exception {
            setupCollectionAndDocument();

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(8);
            processResult.setCharCount(9999);

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertEquals(9999, future.get().getCharCount());
        }

        @Test
        @DisplayName("业务失败时future.get().isSuccess()返回false")
        void businessFailure_setsSuccessFalse() throws Exception {
            setupCollectionAndDocument();

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(false);
            processResult.setErrorMessage("文件解析失败");

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertFalse(future.get().isSuccess());
        }

        @Test
        @DisplayName("业务失败时错误信息正确传播")
        void businessFailure_setsErrorMessage() throws Exception {
            setupCollectionAndDocument();

            DocumentProcessingService.ProcessResult processResult =
                    new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(false);
            processResult.setErrorMessage("文件解析失败：PDF损坏");

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(processResult));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertEquals("文件解析失败：PDF损坏", future.get().getErrorMessage());
        }

        @Test
        @DisplayName("CompletableFuture异常完成时future.get().isSuccess()返回false")
        void exceptionInFuture_setsSuccessFalse() throws Exception {
            setupCollectionAndDocument();

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("MinIO连接超时")));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertFalse(future.get().isSuccess());
        }

        @Test
        @DisplayName("CompletableFuture异常完成时解包CompletionException并提取cause消息")
        void exceptionInFuture_unwrapsCauseMessage() throws Exception {
            setupCollectionAndDocument();

            when(documentProcessingService.processDocument(
                    eq(TEST_DOCUMENT_ID), eq(TEST_COLLECTION_ID),
                    eq("fixed"), eq(true), eq(TEST_USER_ID)))
                    .thenReturn(CompletableFuture.failedFuture(
                            new java.util.concurrent.CompletionException(
                                    new RuntimeException("MinIO连接超时"))));

            CompletableFuture<CollectionBatchService.BatchUploadResult> future =
                    collectionBatchService.processDocumentAsync(
                            TEST_COLLECTION_ID, TEST_DOCUMENT_ID, TEST_USER_ID);

            assertEquals("MinIO连接超时", future.get().getErrorMessage());
        }
    }

    @Nested
    @DisplayName("BatchUploadResult - 结果对象测试")
    class BatchUploadResultTests {

        @Test
        @DisplayName("默认值：success=false，其他字段为null")
        void defaultValues() {
            CollectionBatchService.BatchUploadResult result = new CollectionBatchService.BatchUploadResult();

            assertFalse(result.isSuccess());
            assertNull(result.getDocumentId());
            assertNull(result.getFileName());
            assertNull(result.getFileSize());
            assertNull(result.getChunkCount());
            assertNull(result.getCharCount());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("setSuccess/getSuccess 正确工作")
        void setSuccess() {
            CollectionBatchService.BatchUploadResult result = new CollectionBatchService.BatchUploadResult();

            result.setSuccess(true);
            assertTrue(result.isSuccess());

            result.setSuccess(false);
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("setChunkCount/setCharCount 及所有字段正确工作")
        void setChunkCountAndCharCount() {
            CollectionBatchService.BatchUploadResult result = new CollectionBatchService.BatchUploadResult();

            result.setSuccess(true);
            result.setDocumentId("doc-1");
            result.setFileName("test.pdf");
            result.setFileSize(1024L);
            result.setChunkCount(10);
            result.setCharCount(5000);
            result.setErrorMessage(null);

            assertTrue(result.isSuccess());
            assertEquals("doc-1", result.getDocumentId());
            assertEquals("test.pdf", result.getFileName());
            assertEquals(1024L, result.getFileSize());
            assertEquals(10, result.getChunkCount());
            assertEquals(5000, result.getCharCount());
            assertNull(result.getErrorMessage());
        }
    }
}
