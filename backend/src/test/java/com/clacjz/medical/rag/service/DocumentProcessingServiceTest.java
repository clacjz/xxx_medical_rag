package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.DocType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentProcessingService 单元测试")
class DocumentProcessingServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private DocumentParseService documentParseService;

    @Mock
    private DocumentVectorizationService documentVectorizationService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CollectionDocumentMapper collectionDocumentMapper;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private static final String TEST_DOCUMENT_ID = "doc-123";
    private static final String TEST_COLLECTION_ID = "collection-123";
    private static final String TEST_USER_ID = "user-123";

    @Nested
    @DisplayName("ProcessResult - 结果对象测试")
    class ProcessResultTests {

        @Test
        @DisplayName("默认构造创建空结果")
        void shouldCreateEmptyResult() {
            DocumentProcessingService.ProcessResult result =
                    new DocumentProcessingService.ProcessResult();

            assertFalse(result.isSuccess());
            assertNull(result.getDocumentId());
            assertNull(result.getStoredCount());
            assertNull(result.getCharCount());
            assertNull(result.getErrorMessage());
            assertNull(result.getParseResult());
        }

        @Test
        @DisplayName("设置和获取属性")
        void shouldSetAndGetProperties() {
            DocumentProcessingService.ProcessResult result =
                    new DocumentProcessingService.ProcessResult();

            result.setDocumentId(TEST_DOCUMENT_ID);
            result.setSuccess(true);
            result.setStoredCount(10);
            result.setCharCount(5000);
            result.setErrorMessage(null);

            assertEquals(TEST_DOCUMENT_ID, result.getDocumentId());
            assertTrue(result.isSuccess());
            assertEquals(10, result.getStoredCount());
            assertEquals(5000, result.getCharCount());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("失败结果可以设置错误信息")
        void shouldSetErrorMessageOnFailure() {
            DocumentProcessingService.ProcessResult result =
                    new DocumentProcessingService.ProcessResult();

            result.setSuccess(false);
            result.setErrorMessage("处理失败：文件损坏");

            assertFalse(result.isSuccess());
            assertEquals("处理失败：文件损坏", result.getErrorMessage());
        }

        @Test
        @DisplayName("ProcessResult是静态内部类")
        void shouldBeStaticInnerClass() {
            // ProcessResult 是静态内部类，可以直接实例化
            DocumentProcessingService.ProcessResult result =
                    new DocumentProcessingService.ProcessResult();

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("ErrorHandling - 异常处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("processDocument 异常时返回失败结果")
        void processDocument_returnsFailedResult_onException() throws Exception {
            // Given: parseDocument 抛出运行时异常
            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("解析引擎崩溃"));

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertFalse(result.isSuccess(), "异常时应返回失败结果");
            assertNotNull(result.getErrorMessage(), "异常时应有错误信息");
        }

        @Test
        @DisplayName("processDocument BizException 时错误信息正确传播")
        void processDocument_returnsErrorMessage_onBizException() throws Exception {
            // Given: parseDocument 抛出 BizException
            String bizErrorMsg = "文档格式不支持";
            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenThrow(new BizException(bizErrorMsg));

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then: BizException 的 message 应直接传播
            assertFalse(result.isSuccess());
            assertEquals(bizErrorMsg, result.getErrorMessage(),
                    "BizException 的错误信息应直接传播到 ProcessResult");
        }

        @Test
        @DisplayName("processDocument 通用异常时错误信息包含前缀")
        void processDocument_returnsErrorMessage_onGenericException() throws Exception {
            // Given: parseDocument 抛出通用 Exception
            String errorMsg = "磁盘空间不足";
            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException(errorMsg));

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then: 通用异常的错误信息应带 "处理失败: " 前缀
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains(errorMsg),
                    "通用异常的错误信息应包含原始异常信息");
            assertTrue(result.getErrorMessage().startsWith("处理失败: "),
                    "通用异常的错误信息应以 '处理失败: ' 开头");
        }

        @Test
        @DisplayName("processDocument 解析失败时返回失败结果")
        void processDocument_returnsFailedResult_onParseFailure() throws Exception {
            // Given: parseDocument 返回非 completed 状态
            DocumentParseService.DocumentParseResult failedParseResult =
                    new DocumentParseService.DocumentParseResult(
                            TEST_DOCUMENT_ID, "failed", 0, 0,
                            null, null, null,
                            Collections.emptyList(), "文件损坏无法解析",
                            DocType.NORMAL, null, null);

            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenReturn(failedParseResult);

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertFalse(result.isSuccess(), "解析失败时应返回失败结果");
            assertEquals("文件损坏无法解析", result.getErrorMessage(),
                    "解析失败的错误信息应传播");
            // 不应尝试向量化
            verify(documentVectorizationService, never()).vectorizeMarkdown(
                    anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("processDocument 文档不存在时返回失败结果")
        void processDocument_handlesNullDocument() throws Exception {
            // Given: parseDocument 成功但 documentMapper 返回 null
            DocumentParseService.DocumentParseResult parseResult =
                    new DocumentParseService.DocumentParseResult(
                            TEST_DOCUMENT_ID, "completed", 100, 1,
                            "text", null, null,
                            Collections.emptyList(), null,
                            DocType.NORMAL, null, null);

            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenReturn(parseResult);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(null);

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertFalse(result.isSuccess(), "文档不存在时应返回失败结果");
            assertEquals("文档不存在", result.getErrorMessage(),
                    "文档不存在时错误信息应为 '文档不存在'");
            // 不应尝试向量化
            verify(documentVectorizationService, never()).vectorizeMarkdown(
                    anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("processDocument 无知识库关联时返回失败结果")
        void processDocument_returnsFailed_whenNoCollectionAssociated() throws Exception {
            // Given: parseDocument 成功，文档存在，但无 collectionId 且关联表为空
            DocumentParseService.DocumentParseResult parseResult =
                    new DocumentParseService.DocumentParseResult(
                            TEST_DOCUMENT_ID, "completed", 100, 1,
                            "text", null, null,
                            Collections.emptyList(), null,
                            DocType.NORMAL, null, null);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setTitle("测试文档");

            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenReturn(parseResult);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);
            when(collectionDocumentMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When: collectionId 为 null
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, null, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertFalse(result.isSuccess(), "无知识库关联时应返回失败结果");
            assertEquals("请指定知识库ID，文档尚未关联到任何知识库", result.getErrorMessage());
        }

        @Test
        @DisplayName("processDocument 向量化期间文档被删除时返回失败结果")
        void processDocument_returnsFailed_whenDocumentDeletedDuringVectorization() throws Exception {
            // Given: parseDocument 成功，文档存在，向量化成功，但之后文档被删除
            DocumentParseService.DocumentParseResult parseResult =
                    new DocumentParseService.DocumentParseResult(
                            TEST_DOCUMENT_ID, "completed", 100, 1,
                            "text", null, null,
                            Collections.emptyList(), null,
                            DocType.NORMAL, null, null);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setTitle("测试文档");

            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenReturn(parseResult);
            // 第一次 selectById 返回文档，第二次（向量化后检查）返回 null
            when(documentMapper.selectById(TEST_DOCUMENT_ID))
                    .thenReturn(document)
                    .thenReturn(null);
            when(documentVectorizationService.vectorizeMarkdown(
                    anyString(), eq(TEST_COLLECTION_ID), eq(TEST_DOCUMENT_ID),
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(5);

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertFalse(result.isSuccess(), "向量化期间文档被删除应返回失败结果");
            assertEquals("文档在向量化期间已被删除", result.getErrorMessage());
            // 不应更新文档状态
            verify(documentMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("processDocument 成功时返回正确结果")
        void processDocument_returnsSuccess_onNormalFlow() throws Exception {
            // Given: 完整的正常流程
            DocumentParseService.DocumentParseResult parseResult =
                    new DocumentParseService.DocumentParseResult(
                            TEST_DOCUMENT_ID, "completed", 500, 5,
                            "text content", null, null,
                            Collections.emptyList(), null,
                            DocType.NORMAL, null, null);

            Document document = new Document();
            document.setId(TEST_DOCUMENT_ID);
            document.setTitle("测试文档");

            when(documentParseService.parseDocument(eq(TEST_DOCUMENT_ID), anyString(), anyBoolean()))
                    .thenReturn(parseResult);
            when(documentMapper.selectById(TEST_DOCUMENT_ID)).thenReturn(document);
            when(documentVectorizationService.vectorizeMarkdown(
                    anyString(), eq(TEST_COLLECTION_ID), eq(TEST_DOCUMENT_ID),
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(10);

            // When
            CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    documentProcessingService.processDocument(
                            TEST_DOCUMENT_ID, TEST_COLLECTION_ID, "fixed", false, TEST_USER_ID);
            DocumentProcessingService.ProcessResult result = future.get();

            // Then
            assertTrue(result.isSuccess(), "正常流程应返回成功结果");
            assertEquals(10, result.getStoredCount());
            assertEquals(500, result.getCharCount());
            assertEquals(TEST_DOCUMENT_ID, result.getDocumentId());
            // 应更新文档状态
            verify(documentMapper).updateById(any(Document.class));
        }
    }
}
