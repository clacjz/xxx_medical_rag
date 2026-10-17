package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.DocumentProcessDTO;
import com.clacjz.medical.rag.service.DocumentParseService;
import com.clacjz.medical.rag.service.DocumentPermissionService;
import com.clacjz.medical.rag.service.chunking.ChunkingResult;
import com.clacjz.medical.rag.service.DocumentProcessingService;
import com.clacjz.medical.rag.service.WebhookService;
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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentController 单元测试")
class DocumentControllerTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CollectionDocumentMapper collectionDocumentMapper;

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Mock
    private DocumentParseService documentParseService;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private DocumentController documentController;

    private UserDetails mockUserDetails;
    private Document testDocument;
    private com.clacjz.medical.rag.model.entity.User testUser;

    @BeforeEach
    void setUp() {
        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        testUser = com.clacjz.medical.rag.model.entity.User.builder()
                .id("user-123")
                .username("testuser")
                .build();

        testDocument = Document.builder()
                .id(UUID.randomUUID().toString())
                .title("Test Document")
                .description("Test Description")
                .filePath("/path/to/file.pdf")
                .fileName("file.pdf")
                .fileSize(1024L)
                .fileType("pdf")
                .collectionId("collection-1")
                .ownerId("owner-123")
                .uploadStatus("completed")
                .parseStatus("completed")
                .chunkCount(10)
                .status("active")
                .build();
    }

    @Test
    @DisplayName("获取文档列表成功")
    void listDocuments_Success() {
        Page<Document> page = new Page<>(1, 20);
        page.setRecords(List.of(testDocument));
        page.setTotal(1);
        // MyBatis Plus selectPage uses reference semantics, so we need to use doAnswer to modify the passed page
        doAnswer(invocation -> {
            Page<Document> p = invocation.getArgument(0);
            p.setRecords(List.of(testDocument));
            p.setTotal(1);
            return p;
        }).when(documentMapper).selectPage(any(Page.class), any());

        ResponseEntity<Result<PageResult<Map<String, Object>>>> response =
                documentController.listDocuments(0, 20, null, null, null, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
    }

    @Test
    @DisplayName("获取文档详情成功")
    void getDocument_Success() {
        String docId = testDocument.getId();
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());

        ResponseEntity<Result<Map<String, Object>>> response =
                documentController.getDocument(docId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("删除文档成功")
    void deleteDocument_Success() {
        String docId = testDocument.getId();
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        when(documentMapper.deleteById(docId)).thenReturn(1);
        when(documentPermissionService.canDeleteDocument(any(), any())).thenReturn(true);

        ResponseEntity<Result<Void>> response =
                documentController.deleteDocument(docId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("文档删除成功", response.getBody().getMessage());
    }

    @Test
    @DisplayName("重试文档处理成功")
    void retryDocument_Success() {
        String docId = testDocument.getId();
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);
        doNothing().when(documentPermissionService).checkDocumentOperatePermission(anyString(), any());

        ResponseEntity<Result<Map<String, Object>>> response =
                documentController.retryDocument(docId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("文档处理已重试", response.getBody().getMessage());
    }

    @Test
    @DisplayName("获取分块列表 - 使用DocumentParseService正确解析ProcessResult")
    void getChunks_UsesDocumentParseService() {
        String docId = testDocument.getId();
        testDocument.setProcessResultFile("/tmp/rag-parser/" + docId + "_process.json");

        // 构建 ChunkInfo
        ChunkingResult.ChunkInfo chunkInfo = new ChunkingResult.ChunkInfo(
                0, "Test chunk text", 0, 100, 1, 1, null
        );

        // 构建 ChunkingResult (之前错误地用 ChunkingResult 解析 DocumentProcessResult JSON)
        ChunkingResult chunkResult = ChunkingResult.builder()
                .documentId(docId)
                .totalChars(100)
                .strategy("none")
                .config(new ChunkingResult.ChunkConfig(500, 50))
                .chunkCount(1)
                .chunks(List.of(chunkInfo))
                .previewAt(LocalDateTime.now())
                .build();

        // 构建 DocumentProcessResult (实际存储在 _process.json 中的类型)
        DocumentParseService.DocumentProcessResult processResult =
                new DocumentParseService.DocumentProcessResult(
                        docId,
                        "Original OCR text",
                        new DocumentParseService.DocumentProcessResult.ParseInfo("completed", 100, LocalDateTime.now()),
                        chunkResult
                );

        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());
        // 修复验证：使用 documentParseService.getParseResult() 而不是 chunkingService.loadChunkPreview()
        when(documentParseService.getParseResult(docId)).thenReturn(Optional.of(processResult));

        ResponseEntity<Result<List<DocumentProcessDTO.ChunkDTO>>> response =
                documentController.getChunks(docId, "none", mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Test chunk text", response.getBody().getData().get(0).getText());
    }

    @Test
    @DisplayName("获取分块列表 - processResultFile为空时返回友好提示")
    void getChunks_NoProcessResultFile() {
        String docId = testDocument.getId();
        // 不设置 processResultFile

        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());

        ResponseEntity<Result<List<DocumentProcessDTO.ChunkDTO>>> response =
                documentController.getChunks(docId, "none", mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("请先执行文档处理", response.getBody().getMessage());
    }

    @Nested
    @DisplayName("回调URL流程测试")
    class CallbackUrlFlowTests {

        private String docId;

        @BeforeEach
        void setUpCallback() {
            docId = testDocument.getId();
            when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
            doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());
        }

        @Test
        @DisplayName("processDocument提供callbackUrl时调用validateCallbackUrl - 无效URL抛出BizException")
        void processDocumentWithCallbackUrl_validatesUrl() {
            // 使用ftp协议的URL，validateCallbackUrl应拒绝并抛出BizException
            String invalidUrl = "ftp://evil-server.com/callback";

            BizException exception = assertThrows(BizException.class,
                    () -> documentController.processDocument(
                            docId, "fixed", null, invalidUrl, mockUserDetails));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("http/https"));
        }

        @Test
        @DisplayName("processDocument callbackUrl为null时跳过验证")
        void processDocumentWithNullCallbackUrl_skipsValidation() throws Exception {
            DocumentParseService.DocumentParseResult parseResult = new DocumentParseService.DocumentParseResult(
                    docId, "completed", 100, 1, "Test text", null, null, null, null, null, null, null);

            DocumentProcessingService.ProcessResult processResult = new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(5);
            processResult.setParseResult(parseResult);

            java.util.concurrent.CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    java.util.concurrent.CompletableFuture.completedFuture(processResult);
            when(documentProcessingService.processDocument(anyString(), any(), anyString(), anyBoolean(), anyString()))
                    .thenReturn(future);

            // callbackUrl为null，不应触发validateCallbackUrl，也不应调用webhookService
            ResponseEntity<Result<?>> response = documentController.processDocument(
                    docId, "fixed", null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(webhookService, never()).sendCallback(anyString(), anyString(), anyBoolean(), any(), anyString());
        }

        @Test
        @DisplayName("processDocument callbackUrl为空白字符串时跳过验证")
        void processDocumentWithBlankCallbackUrl_skipsValidation() throws Exception {
            DocumentParseService.DocumentParseResult parseResult = new DocumentParseService.DocumentParseResult(
                    docId, "completed", 100, 1, "Test text", null, null, null, null, null, null, null);

            DocumentProcessingService.ProcessResult processResult = new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(5);
            processResult.setParseResult(parseResult);

            java.util.concurrent.CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    java.util.concurrent.CompletableFuture.completedFuture(processResult);
            when(documentProcessingService.processDocument(anyString(), any(), anyString(), anyBoolean(), anyString()))
                    .thenReturn(future);

            // callbackUrl为空白字符串，不应触发validateCallbackUrl，也不应调用webhookService
            ResponseEntity<Result<?>> response = documentController.processDocument(
                    docId, "fixed", null, "   ", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(webhookService, never()).sendCallback(anyString(), anyString(), anyBoolean(), any(), anyString());
        }

        @Test
        @DisplayName("processDocument回调URL指向内网10.x.x.x被拒绝")
        void processDocument_withInternalCallbackUrl_rejected() {
            // 10.x.x.x 属于 SiteLocalAddress，validateCallbackUrl 应拒绝
            String internalUrl = "http://localhost/callback";

            BizException exception = assertThrows(BizException.class,
                    () -> documentController.processDocument(
                            docId, "fixed", null, internalUrl, mockUserDetails));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("内网地址"));
        }

        @Test
        @DisplayName("processDocument回调URL使用ftp协议被拒绝")
        void processDocument_withFtpCallbackUrl_rejected() {
            // ftp:// 协议不在 http/https 白名单中，应被拒绝
            String ftpUrl = "ftp://evil-server.com/callback";

            BizException exception = assertThrows(BizException.class,
                    () -> documentController.processDocument(
                            docId, "fixed", null, ftpUrl, mockUserDetails));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("http/https"));
        }

        @Test
        @DisplayName("processDocument回调URL使用合法https地址被接受")
        void processDocument_withValidCallbackUrl_accepted() throws Exception {
            DocumentParseService.DocumentParseResult parseResult = new DocumentParseService.DocumentParseResult(
                    docId, "completed", 100, 1, "Test text", null, null, null, null, null, null, null);

            DocumentProcessingService.ProcessResult processResult = new DocumentProcessingService.ProcessResult();
            processResult.setSuccess(true);
            processResult.setStoredCount(5);
            processResult.setParseResult(parseResult);

            java.util.concurrent.CompletableFuture<DocumentProcessingService.ProcessResult> future =
                    java.util.concurrent.CompletableFuture.completedFuture(processResult);
            when(documentProcessingService.processDocument(anyString(), any(), anyString(), anyBoolean(), anyString()))
                    .thenReturn(future);

            // https://example.com 是合法外部地址，应通过验证并进入回调模式
            ResponseEntity<Result<?>> response = documentController.processDocument(
                    docId, "fixed", null, "https://example.com/callback", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            // 回调模式返回 Result.success("处理已启动，完成后将通过回调通知")
            // 单参数success(T data)将消息存入data字段，message为默认"操作成功"
            assertEquals("操作成功", response.getBody().getMessage());
            assertEquals("处理已启动，完成后将通过回调通知", response.getBody().getData());
        }
    }

    @Test
    @DisplayName("获取文档详情 - 文档存在时返回OK")
    void getDocument_existingDocument_returnsOk() {
        String docId = testDocument.getId();
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());

        ResponseEntity<Result<Map<String, Object>>> response =
                documentController.getDocument(docId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertEquals(docId, response.getBody().getData().get("id"));
        assertEquals("Test Document", response.getBody().getData().get("title"));
    }

    @Test
    @DisplayName("获取文档详情 - 文档不存在时抛出RuntimeException")
    void getDocument_nonExistentDocument_returnsNotFound() {
        String docId = "non-existent-id";
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(null);
        doNothing().when(documentPermissionService).checkPermission(anyString(), any(), any());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> documentController.getDocument(docId, mockUserDetails));

        assertTrue(exception.getMessage().contains("Document not found"));
    }

    @Test
    @DisplayName("删除文档 - 文档存在且用户有权限时删除成功")
    void deleteDocument_existingDocument_succeeds() {
        String docId = testDocument.getId();
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        when(documentMapper.selectById(docId)).thenReturn(testDocument);
        when(documentPermissionService.canDeleteDocument(any(), any())).thenReturn(true);
        when(documentMapper.deleteById(docId)).thenReturn(1);

        ResponseEntity<Result<Void>> response =
                documentController.deleteDocument(docId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("文档删除成功", response.getBody().getMessage());
        verify(documentMapper).deleteById(docId);
    }
}