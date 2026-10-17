package com.clacjz.medical.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.dto.DocumentProcessDTO;
import com.clacjz.medical.rag.model.dto.OcrResultDTO;
import com.clacjz.medical.rag.model.dto.PdfInfoDTO;
import com.clacjz.medical.rag.model.dto.ProcessStatusDTO;
import com.clacjz.medical.rag.model.dto.SaveOcrMarkdownRequest;
import com.clacjz.medical.rag.model.enums.OcrStatus;
import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import com.clacjz.medical.rag.service.DocumentProcessingService;
import com.clacjz.medical.rag.service.DocumentVectorizationService;
import com.clacjz.medical.rag.service.EsIndexService;
import com.clacjz.medical.rag.service.OcrService;
import com.clacjz.medical.rag.service.VectorCrudService;
import com.clacjz.medical.rag.service.WebhookService;
import com.clacjz.medical.rag.util.FileNameUtil;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.DocumentParseService;
import com.clacjz.medical.rag.service.DocumentPermissionService;
import com.clacjz.medical.rag.service.chunking.ChunkingService;
import com.clacjz.medical.rag.service.chunking.ChunkingResult;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Document API", description = "文档管理相关接口")
public class DocumentController {

    private final DocumentMapper documentMapper;
    private final CollectionMapper collectionMapper;
    private final CollectionDocumentMapper collectionDocumentMapper;
    private final UserMapper userMapper;
    private final DocumentParseService documentParseService;
    private final ChunkingService chunkingService;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentPermissionService documentPermissionService;
    private final OcrService ocrService;
    private final MinioClient minioClient;
    private final RAGProperties ragProperties;
    private final DocumentVectorizationService documentVectorizationService;
    private final VectorCrudService vectorCrudService;
    private final EsIndexService esIndexService;
    private final WebhookService webhookService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文档到MinIO对象存储，并创建文档记录")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN', 'DEPT_UPLOADER')")
    public ResponseEntity<Result<Map<String, Object>>> uploadDocument(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "知识库ID") @RequestParam(required = false) String collectionId,
            @Parameter(description = "文档描述") @RequestParam(required = false) String description,
            @Parameter(description = "权限类型") @RequestParam(required = false) String permissionType,
            @Parameter(description = "指定可访问的用户ID列表") @RequestParam(required = false) java.util.List<String> allowedUserIds,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.uploadDocument");
        log.info("Upload document: fileName={}, size={}, collectionId={}, user={}",
                file.getOriginalFilename(), file.getSize(), collectionId, user.getUsername());

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件名不能为空");
        }

        String sanitizedFilename = FileNameUtil.sanitizeFilename(originalFilename);

        String fileExtension = FileNameUtil.getFileExtension(sanitizedFilename);
        if (!isAllowedFileType(fileExtension)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "不支持的文件类型: " + fileExtension + "，支持的类型: pdf, doc, docx, xlsx, xls, ppt, pptx, md, txt");
        }

        if (file.getSize() > 100 * 1024 * 1024) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件大小不能超过100MB");
        }

        String documentId = UUID.randomUUID().toString();
        String objectName = documentId + "/" + sanitizedFilename;

        try {
            String bucketName = ragProperties.getMinio().getBucket().getDocuments();
            ensureBucketExists(bucketName);

            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.debug("File uploaded to MinIO: bucket={}, object={}", bucketName, objectName);

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", documentId, e);
            throw new BizException(ErrorCode.OPERATION_FAILED, "文件上传失败: " + e.getMessage());
        }

        Document document = new Document();
        document.setId(documentId);
        document.setTitle(sanitizedFilename);
        document.setFileName(sanitizedFilename);
        document.setFilePath(objectName);
        document.setFileType(fileExtension);
        document.setFileSize(file.getSize());
        // 注意：documents.collection_id 仅作历史兼容，不再用于建立关联关系
        // 文档与知识库的多对多关联完全通过 collection_documents 表管理
        document.setOwnerId(user.getId());
        document.setDepartmentId(TenantContext.getDepartmentId());
        document.setUploadStatus("completed");
        document.setParseStatus("pending");
        document.setChunkStatus("pending");
        document.setDescription(description);
        document.setStatus("active");

        documentMapper.insert(document);
        log.info("Document record created: id={}, title={}", documentId, originalFilename);

        // 设置文档权限
        if (permissionType != null && !permissionType.isBlank()) {
            try {
                var permRequest = com.clacjz.medical.rag.model.dto.UpdateDocumentPermissionRequest.builder()
                        .permissionType(com.clacjz.medical.rag.model.enums.PermissionTypeEnums.valueOf(permissionType))
                        .allowedUserIds(allowedUserIds)
                        .build();
                documentPermissionService.updateDocumentPermission(
                        documentId, permRequest, user.getId(), null, null);
                log.info("Document permission set during upload: documentId={}, permissionType={}",
                        documentId, permissionType);
            } catch (Exception e) {
                log.warn("Failed to set document permission during upload: documentId={}, error={}",
                        documentId, e.getMessage());
            }
        }

        // 建立文档与知识库的关联关系
        if (collectionId != null && !collectionId.isBlank()) {
            CollectionDocument cd = CollectionDocument.builder()
                    .collectionId(collectionId)
                    .documentId(documentId)
                    .departmentId(TenantContext.getDepartmentId())
                    .addedBy(user.getId())
                    .processingStatus("PENDING")
                    .build();
            collectionDocumentMapper.insert(cd);
            log.info("Document-Collection association created: documentId={}, collectionId={}", documentId, collectionId);
        }

        Map<String, Object> result = convertToMap(document, user.getUsername());
        return ResponseEntity.ok(Result.success("文档上传成功", result));
    }

    @PostMapping(value = "/upload-for-ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档用于OCR预览", description = "临时上传文档用于OCR预览，不创建正式文档记录")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> uploadForOcr(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.uploadForOcr");
        log.info("Upload for OCR: fileName={}, size={}, user={}",
                file.getOriginalFilename(), file.getSize(), user.getUsername());

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件名不能为空");
        }

        String sanitizedFilename = FileNameUtil.sanitizeFilename(originalFilename);
        String fileExtension = FileNameUtil.getFileExtension(sanitizedFilename);

        if (!"pdf".equalsIgnoreCase(fileExtension)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "OCR预览仅支持PDF文件");
        }

        if (file.getSize() > 100 * 1024 * 1024) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件大小不能超过100MB");
        }

        String tempDocumentId = "temp-" + UUID.randomUUID().toString();
        String objectName = tempDocumentId + "/" + sanitizedFilename;

        try {
            String bucketName = ragProperties.getMinio().getBucket().getDocuments();
            ensureBucketExists(bucketName);

            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.debug("Temp file uploaded to MinIO: bucket={}, object={}", bucketName, objectName);

            Map<String, Object> result = new HashMap<>();
            result.put("id", tempDocumentId);
            result.put("fileName", sanitizedFilename);
            result.put("filePath", objectName);
            result.put("fileType", fileExtension);
            result.put("fileSize", file.getSize());
            return ResponseEntity.ok(Result.success(result));

        } catch (Exception e) {
            log.error("Failed to upload temp file for OCR: {}", tempDocumentId, e);
            throw new BizException(ErrorCode.OPERATION_FAILED, "文件上传失败: " + e.getMessage());
        }
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("Created MinIO bucket: {}", bucketName);
        }
    }

    private boolean isAllowedFileType(String extension) {
        String[] allowedTypes = ragProperties.getDocument().getSupportedTypes();
        for (String type : allowedTypes) {
            if (type.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    @Operation(summary = "获取文档列表", description = "分页获取文档列表，支持按知识库和状态筛选")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PageResult<Map<String, Object>>>> listDocuments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "知识库ID") @RequestParam(required = false) String collectionId,
            @Parameter(description = "文档状态") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DocumentController.listDocuments");

        log.info("List documents: page={}, pageSize={}, collectionId={}, status={}, keyword={}, user={}",
                page, pageSize, collectionId, status, keyword, username);

        Page<Document> mpPage = new Page<>(page + 1, pageSize);

        IPage<Document> documentPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            documentMapper.selectByTitleContainingIgnoreCase(mpPage, keyword.trim());
            documentPage = mpPage;
        } else if (collectionId != null && !collectionId.isEmpty()) {
            documentMapper.selectByCollectionId(mpPage, collectionId);
            documentPage = mpPage;
        } else if (status != null && !status.isEmpty()) {
            documentMapper.selectByStatus(mpPage, status);
            documentPage = mpPage;
        } else {
            documentPage = documentMapper.selectPage(mpPage, null);
        }

        List<Map<String, Object>> items = documentPage.getRecords().stream()
                .map(doc -> convertToMap(doc, username))
                .collect(Collectors.toList());
        PageResult<Map<String, Object>> result = PageResult.<Map<String, Object>>builder()
                .items(items)
                .total(documentPage.getTotal())
                .page((int) documentPage.getCurrent())
                .pageSize((int) documentPage.getSize())
                .totalPages((int) documentPage.getPages())
                .build();

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情", description = "根据ID获取文档详细信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> getDocument(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

                        User user = getUserSafely(userDetails, "DocumentController.getDocument");
        log.info("Get document: id={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        Map<String, Object> result = convertToMap(document, user.getUsername());
        return ResponseEntity.ok(Result.success(result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "MinIO硬删除，数据库软删除，Qdrant/ES软删除")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> deleteDocument(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.deleteDocument");
        log.info("Delete document: id={}, user={}, role={}", id, user.getUsername(), user.getRole());

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        // 文档正在处理中，防止并发删除导致数据不一致
        if ("processing".equals(document.getParseStatus())) {
            throw new BizException(ErrorCode.OPERATION_FAILED,
                    "文档正在处理中，请等待处理完成后再删除");
        }

        if (!documentPermissionService.canDeleteDocument(user, document)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权删除此文档");
        }

        // 1. MinIO 硬删除（删除文件）
        try {
            String bucketName = ragProperties.getMinio().getBucket().getDocuments();
            String filePath = document.getFilePath();
            if (filePath != null && !filePath.isBlank()) {
                minioClient.removeObject(
                        io.minio.RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(filePath)
                                .build()
                );
                log.info("MinIO file deleted: bucket={}, path={}", bucketName, filePath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete MinIO file: documentId={}: {}", id, e);
            // MinIO 删除失败不影响后续操作
        }

        // 2. 获取文档关联的所有知识库，从 Qdrant 和 ES 中清理向量
        QueryWrapper<CollectionDocument> cdWrapper = new QueryWrapper<>();
        cdWrapper.eq("document_id", id);
        List<CollectionDocument> relatedCDs = collectionDocumentMapper.selectList(cdWrapper);

        // 清理各知识库的 Qdrant 向量
        for (CollectionDocument cd : relatedCDs) {
            try {
                vectorCrudService.deleteByDocumentId(cd.getCollectionId(), id);
                log.info("Qdrant vectors deleted: collectionId={}, documentId={}", cd.getCollectionId(), id);
            } catch (Exception e) {
                log.warn("Failed to delete Qdrant vectors: documentId={}, collectionId={}, error={}",
                        id, cd.getCollectionId(), e.getMessage());
            }
            // 清理 ES 索引
            try {
                esIndexService.deleteDocument(cd.getCollectionId(), id);
                log.info("ES document deleted: collectionId={}, documentId={}", cd.getCollectionId(), id);
            } catch (Exception e) {
                log.warn("Failed to delete ES document: documentId={}, collectionId={}, error={}",
                        id, cd.getCollectionId(), e.getMessage());
            }
        }

        // 3. 删除 collection_documents 关联记录
        collectionDocumentMapper.delete(new QueryWrapper<CollectionDocument>().eq("document_id", id));

        // 4. 数据库软删除
        documentMapper.deleteById(id);
        log.info("Document soft-deleted: id={}, title={}, triggeredBy={}", id, document.getTitle(), user.getUsername());

        log.info("Document deleted successfully: id={}, triggeredBy={}", id, user.getUsername());
        return ResponseEntity.ok(Result.success("文档删除成功", null));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试文档处理", description = "重新处理上传失败的文档")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> retryDocument(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.retryDocument");
        log.info("Retry document processing: id={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.EDIT, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        document.setUploadStatus("pending");
        document.setParseStatus("pending");
        documentMapper.updateById(document);

        Map<String, Object> result = convertToMap(document, user.getUsername());
        return ResponseEntity.ok(Result.success("文档处理已重试", result));
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "触发文档处理", description = "解析文档、分块、向量化存储到知识库；支持 callbackUrl 实现异步回调")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<?>> processDocument(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "分块策略: fixed/paragraph/semantic") @RequestParam(defaultValue = "fixed") String strategy,
            @Parameter(description = "知识库ID") @RequestParam(required = false) String collectionId,
            @Parameter(description = "处理完成回调URL（非阻塞模式）") @RequestParam(required = false) String callbackUrl,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        User user = getUserSafely(userDetails, "DocumentController.processDocument");
        log.info("Process document: id={}, strategy={}, collectionId={}, callbackUrl={}, user={}",
                id, strategy, collectionId, callbackUrl, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.EDIT, user);

        // 异步触发处理（@Async 返回 CompletableFuture）
        java.util.concurrent.CompletableFuture<DocumentProcessingService.ProcessResult> future =
                documentProcessingService.processDocument(id, collectionId, strategy, true, user.getId());

        // --- 回调模式：不阻塞，处理完成后异步通知 ---
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            // SSRF防护：仅允许http/https协议，禁止内网地址
            validateCallbackUrl(callbackUrl);
            future.whenCompleteAsync((processResult, ex) -> {
                if (ex != null) {
                    webhookService.sendCallback(callbackUrl, id, false, null, "系统异常: " + ex.getMessage());
                    return;
                }
                webhookService.sendCallback(
                        callbackUrl, id, processResult.isSuccess(),
                        processResult.getStoredCount(),
                        processResult.isSuccess() ? null : processResult.getErrorMessage()
                );
            });
            log.info("Process document (callback mode): id={}, will callback to {}", id, callbackUrl);
            return ResponseEntity.ok(Result.success("处理已启动，完成后将通过回调通知"));
        }

        // --- 阻塞模式：等待处理完成（保持向后兼容）---
        DocumentProcessingService.ProcessResult processResult = future.get();

        if (!processResult.isSuccess()) {
            throw new BizException(ErrorCode.OPERATION_FAILED,
                    String.format("文档处理失败: %s", processResult.getErrorMessage()));
        }

        // 获取完整的解析结果用于返回
        DocumentProcessDTO dto = convertToProcessDTO(processResult.getParseResult());

        return ResponseEntity.ok(Result.success("文档处理完成，已存储 " + processResult.getStoredCount() + " 个向量", dto));
    }

    @GetMapping("/{id}/process/status")
    @Operation(summary = "查询处理状态", description = "获取文档解析和分块处理状态")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ProcessStatusDTO>> getProcessStatus(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getProcessStatus");
        log.info("Get process status: id={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        DocumentParseService.DocumentParseStatus status = documentParseService.getParseStatus(id);

        ProcessStatusDTO dto = ProcessStatusDTO.builder()
                .documentId(status.documentId())
                .uploadStatus(status.uploadStatus())
                .parseStatus(status.parseStatus())
                .chunkStatus(status.chunkStatus())
                .vectorStatus(null)
                .progress(calculateProgress(status))
                .currentStep(determineCurrentStep(status))
                .startTime(status.lastParseAt())
                .build();

        return ResponseEntity.ok(Result.success(dto));
    }

    @GetMapping("/{id}/process/result")
    @Operation(summary = "获取处理结果", description = "获取文档解析和分块的完整结果")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<DocumentProcessDTO>> getProcessResult(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getProcessResult");
        log.info("Get process result: id={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Optional<DocumentParseService.DocumentProcessResult> result =
                documentParseService.getParseResult(id);

        if (result.isEmpty()) {
            return ResponseEntity.ok(Result.success("处理结果不存在", null));
        }

        DocumentProcessDTO dto = convertToProcessDTO(result.get());
        return ResponseEntity.ok(Result.success(dto));
    }

    @GetMapping("/{id}/chunks")
    @Operation(summary = "获取分块列表", description = "获取文档的分块预览列表")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<List<DocumentProcessDTO.ChunkDTO>>> getChunks(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "分块策略") @RequestParam(defaultValue = "fixed") String strategy,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getChunks");
        log.info("Get chunks: id={}, strategy={}, user={}", id, strategy, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        if (document.getProcessResultFile() == null) {
            return ResponseEntity.ok(Result.success("请先执行文档处理", null));
        }

        // process_result_file 存储的是 DocumentProcessResult JSON（包含 originalText 和 chunkPreview）
        // 不能用 ChunkingResult 直接解析，需要通过 documentParseService 获取
        Optional<DocumentParseService.DocumentProcessResult> parseResult =
                documentParseService.getParseResult(id);
        if (parseResult.isEmpty()) {
            throw new RuntimeException("无法加载文档处理结果: " + id);
        }

        ChunkingResult chunkResult = parseResult.get().chunkPreview();
        if (chunkResult == null || chunkResult.chunks() == null) {
            throw new RuntimeException("分块预览为空，请重新处理文档: " + id);
        }

        List<DocumentProcessDTO.ChunkDTO> chunks = chunkResult.chunks().stream()
                .map(c -> new DocumentProcessDTO.ChunkDTO(
                        c.index(), c.text(), c.startPos(), c.endPos(), c.startPage(), c.endPage()))
                .toList();

        return ResponseEntity.ok(Result.success(chunks));
    }

    @GetMapping("/{id}/ocr")
    @Operation(summary = "OCR预览", description = "获取文档指定页的OCR识别结果")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OcrResultDTO>> getOcrResult(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getOcrResult");
        log.info("Get OCR result: documentId={}, page={}, user={}", id, page, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        if (!"pdf".equalsIgnoreCase(document.getFileType())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "当前仅支持PDF文档的OCR预览");
        }

        // 优先使用已保存的解析结果（包含OCR文本），避免重复调用OCR服务
        if (page == 1) {
            Optional<DocumentParseService.DocumentProcessResult> parseResult =
                    documentParseService.getParseResult(id);
            if (parseResult.isPresent() && parseResult.get().originalText() != null
                    && !parseResult.get().originalText().isEmpty()) {
                String originalText = parseResult.get().originalText();
                OcrResultDTO dto = OcrResultDTO.builder()
                        .documentId(id)
                        .currentPage(1)
                        .totalPages(1)
                        .ocrText(originalText)
                        .htmlTable("")
                        .status(OcrStatus.SUCCESS)
                        .build();
                log.info("Using saved parse result for OCR preview: documentId={}, textLength={}", id, originalText.length());
                return ResponseEntity.ok(Result.success(dto));
            }
        }

        // 未找到保存的结果或非第一页，调用OCR服务（结果会被Redis缓存）
        OcrResultDTO result = ocrService.recognizePage(id, document.getFilePath(), page);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/ocr-preview")
    @Operation(summary = "OCR预览(临时文档)", description = "通过文件路径获取临时文档的OCR识别结果")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OcrResultDTO>> ocrPreview(
            @Parameter(description = "文档ID") @RequestParam String documentId,
            @Parameter(description = "文件路径") @RequestParam String filePath,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.ocrPreview");
        log.info("OCR preview: documentId={}, filePath={}, page={}, user={}",
                documentId, filePath, page, user.getUsername());

        // 权限检查：temp-前缀表示临时上传的文档，无需额外权限检查；
        // 非temp文档需验证用户对该文档的读取权限
        if (!documentId.startsWith("temp-")) {
            documentPermissionService.checkPermission(documentId, PermissionLevelEnums.READ, user);
        }

        OcrResultDTO result = ocrService.recognizePage(documentId, filePath, page);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/ocr/save")
    @Operation(summary = "保存OCR结果到知识库", description = "将OCR识别的Markdown文本向量化并存储到指定知识库的Qdrant中")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN', 'DEPT_UPLOADER')")
    public ResponseEntity<Result<Map<String, Object>>> saveOcrResult(
            @Valid @RequestBody SaveOcrMarkdownRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.saveOcrResult");

        // 后端自动生成 documentId（前端传递的 ID 可能来自客户端随机生成，存在冲突风险）
        if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            request.setDocumentId("ocr-" + UUID.randomUUID());
        }

        log.info("Save OCR result: collectionId={}, documentId={}, title={}, contentLength={}, updateContent={}, user={}",
                request.getCollectionId(), request.getDocumentId(), request.getTitle(),
                request.getMarkdownContent().length(), request.getUpdateContent(), user.getUsername());

        int storedCount;

        // 如果是更新操作，先删除旧数据，再重新存储
        if (Boolean.TRUE.equals(request.getUpdateContent())) {
            storedCount = documentVectorizationService.updateDocumentContent(
                    request.getMarkdownContent(),
                    request.getCollectionId(),
                    request.getDocumentId(),
                    request.getTitle(),
                    request.getStrategy()
            );
        } else {
            // 新增操作
            storedCount = documentVectorizationService.vectorizeMarkdown(
                    request.getMarkdownContent(),
                    request.getCollectionId(),
                    request.getDocumentId(),
                    request.getTitle(),
                    request.getStrategy()
            );
        }

        // 设置文档权限
        if (request.getPermissionType() != null) {
            try {
                var permRequest = com.clacjz.medical.rag.model.dto.UpdateDocumentPermissionRequest.builder()
                        .permissionType(com.clacjz.medical.rag.model.enums.PermissionTypeEnums.valueOf(request.getPermissionType()))
                        .allowedUserIds(request.getAllowedUserIds())
                        .build();
                documentPermissionService.updateDocumentPermission(
                        request.getDocumentId(), permRequest, user.getId(), null, null);
                log.info("Document permission set: documentId={}, permissionType={}",
                        request.getDocumentId(), request.getPermissionType());
            } catch (Exception e) {
                log.warn("Failed to set document permission: documentId={}, error={}",
                        request.getDocumentId(), e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", request.getDocumentId());
        result.put("collectionId", request.getCollectionId());
        result.put("storedChunks", storedCount);
        result.put("title", request.getTitle());

        log.info("OCR result saved successfully: collectionId={}, documentId={}, storedCount={}, updateContent={}",
                request.getCollectionId(), request.getDocumentId(), storedCount, request.getUpdateContent());

        return ResponseEntity.ok(Result.success("OCR结果已保存到知识库", result));
    }

    @DeleteMapping("/{documentId}/ocr-save")
    @Operation(summary = "取消OCR保存", description = "取消OCR保存操作，清理已生成的向量")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN', 'DEPT_UPLOADER')")
    public ResponseEntity<Result<Void>> cancelOcrSave(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Parameter(description = "知识库ID") @RequestParam String collectionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.cancelOcrSave");
        log.info("Cancel OCR save: documentId={}, collectionId={}, user={}",
                documentId, collectionId, user.getUsername());

        // 删除Qdrant中该文档的所有向量
        vectorCrudService.deleteByDocumentId(collectionId, documentId);

        return ResponseEntity.ok(Result.success("已取消保存并清理向量", null));
    }

    @GetMapping("/{id}/pdf/need-ocr")
    @Operation(summary = "判断PDF是否需要OCR", description = "检测PDF是否可以提取文字，返回是否需要OCR")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> checkPdfNeedOcr(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.checkPdfNeedOcr");
        log.info("Check PDF need OCR: documentId={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        if (!"pdf".equalsIgnoreCase(document.getFileType())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "当前仅支持PDF文档");
        }

        // 使用PDF解析器检测文字提取量
        String bucketName = ragProperties.getMinio().getBucket().getDocuments();
        String filePath = document.getFilePath();

        int totalPages = 0;
        int extractablePages = 0;

        try (InputStream is = minioClient.getObject(
                io.minio.GetObjectArgs.builder().bucket(bucketName).object(filePath).build())) {

            byte[] pdfBytes = is.readAllBytes();

            try (var pdfDocument = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                totalPages = pdfDocument.getNumberOfPages();

                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(totalPages, 3));
                String text = stripper.getText(pdfDocument);
                extractablePages = text != null && text.trim().length() > 100 ? totalPages : 0;
            }

        } catch (Exception e) {
            log.warn("PDF文字检测失败: documentId={}", id);
        }

        boolean needOcr = extractablePages == 0 && totalPages > 0;

        Map<String, Object> result = new HashMap<>();
        result.put("needOcr", needOcr);
        result.put("pageCount", totalPages);
        result.put("extractablePages", extractablePages);

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}/pdf/info")
    @Operation(summary = "获取PDF信息", description = "获取PDF文档的基本信息（总页数等）")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PdfInfoDTO>> getPdfInfo(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getPdfInfo");
        log.info("Get PDF info: documentId={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        if (!"pdf".equalsIgnoreCase(document.getFileType())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "当前仅支持PDF文档");
        }

        PdfInfoDTO pdfInfo = PdfInfoDTO.builder()
                .documentId(id)
                .filePath(document.getFilePath())
                .fileType(document.getFileType())
                .build();

        return ResponseEntity.ok(Result.success(pdfInfo));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "获取原始文件", description = "获取MinIO中的原始文档文件用于预览")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getDocumentFile(
            @Parameter(description = "文档ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getDocumentFile");
        log.info("Get document file: id={}, user={}", id, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        String bucketName = ragProperties.getMinio().getBucket().getDocuments();
        String filePath = document.getFilePath();

        try (InputStream is = minioClient.getObject(
                io.minio.GetObjectArgs.builder().bucket(bucketName).object(filePath).build())) {
            byte[] content = is.readAllBytes();

            String contentType = getContentType(document.getFileType());
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(document.getFileName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", disposition.toString())
                    .body(content);
        } catch (Exception e) {
            log.error("Failed to get document file: id={}", id, e);
            throw new RuntimeException("Failed to get document file: " + e.getMessage(), e);
        }
    }

    private String getContentType(String fileType) {
        if (fileType == null) return "application/octet-stream";
        return switch (fileType.toUpperCase()) {
            case "PDF" -> "application/pdf";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "XLS" -> "application/vnd.ms-excel";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "DOC" -> "application/msword";
            case "TXT" -> "text/plain";
            case "MARKDOWN", "MD" -> "text/markdown";
            default -> "application/octet-stream";
        };
    }

    @GetMapping("/{id}/text")
    @Operation(summary = "获取原始文本", description = "获取文档解析后的原始文本内容")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> getDocumentText(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "是否截断长文本") @RequestParam(defaultValue = "true") boolean truncate,
            @Parameter(description = "最大字符数") @RequestParam(defaultValue = "50000") int maxLength,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "DocumentController.getDocumentText");
        log.info("Get document text: id={}, truncate={}, user={}", id, truncate, user.getUsername());

        documentPermissionService.checkPermission(id, PermissionLevelEnums.READ, user);

        Optional<DocumentParseService.DocumentProcessResult> result =
                documentParseService.getParseResult(id);

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", id);

        if (result.isPresent()) {
            String text = result.get().originalText();
            if (truncate && text.length() > maxLength) {
                text = text.substring(0, maxLength) + "...";
                response.put("truncated", true);
                response.put("originalLength", result.get().originalText().length());
            } else {
                response.put("truncated", false);
            }
            response.put("charCount", result.get().originalText().length());
            response.put("text", text);
        } else {
            response.put("text", null);
            response.put("message", "请先执行文档处理");
        }

        return ResponseEntity.ok(Result.success(response));
    }

    private DocumentProcessDTO convertToProcessDTO(DocumentParseService.DocumentParseResult result) {
        DocumentProcessDTO dto = new DocumentProcessDTO();
        dto.setDocumentId(result.documentId());
        dto.setOriginalText(result.originalText());

        DocumentProcessDTO.ParseResultDTO parseResultDTO = new DocumentProcessDTO.ParseResultDTO();
        parseResultDTO.setStatus(result.status());
        parseResultDTO.setCharCount(result.charCount());
        parseResultDTO.setOriginalText(result.originalText());
        dto.setParseResult(parseResultDTO);

        if (result.chunkPreview() != null) {
            ChunkingResult cr = result.chunkPreview();
            DocumentProcessDTO.ChunkPreviewDTO chunkPreview = new DocumentProcessDTO.ChunkPreviewDTO();
            chunkPreview.setTotalChars(cr.totalChars());
            chunkPreview.setStrategy(cr.strategy());
            chunkPreview.setChunkCount(cr.chunkCount());
            chunkPreview.setPreviewAt(cr.previewAt());
            chunkPreview.setConfig(new DocumentProcessDTO.ChunkConfigDTO(
                    cr.config().chunkSize(), cr.config().overlap()));
            chunkPreview.setChunks(cr.chunks().stream()
                    .map(c -> new DocumentProcessDTO.ChunkDTO(
                            c.index(), c.text(), c.startPos(), c.endPos(), c.startPage(), c.endPage()))
                    .toList());
            dto.setChunkPreview(chunkPreview);
        }

        return dto;
    }

    private DocumentProcessDTO convertToProcessDTO(DocumentParseService.DocumentProcessResult result) {
        DocumentProcessDTO dto = new DocumentProcessDTO();
        dto.setDocumentId(result.documentId());
        dto.setOriginalText(result.originalText());

        if (result.chunkPreview() != null) {
            ChunkingResult cr = result.chunkPreview();
            DocumentProcessDTO.ChunkPreviewDTO chunkPreview = new DocumentProcessDTO.ChunkPreviewDTO();
            chunkPreview.setTotalChars(cr.totalChars());
            chunkPreview.setStrategy(cr.strategy());
            chunkPreview.setChunkCount(cr.chunkCount());
            chunkPreview.setPreviewAt(cr.previewAt());
            chunkPreview.setConfig(new DocumentProcessDTO.ChunkConfigDTO(
                    cr.config().chunkSize(), cr.config().overlap()));
            chunkPreview.setChunks(cr.chunks().stream()
                    .map(c -> new DocumentProcessDTO.ChunkDTO(
                            c.index(), c.text(), c.startPos(), c.endPos(), c.startPage(), c.endPage()))
                    .toList());
            dto.setChunkPreview(chunkPreview);
        }

        return dto;
    }

    private int calculateProgress(DocumentParseService.DocumentParseStatus status) {
        int progress = 0;
        if ("completed".equals(status.uploadStatus())) progress += 20;
        if ("completed".equals(status.parseStatus())) progress += 40;
        if ("completed".equals(status.chunkStatus())) progress += 40;
        return progress;
    }

    private int calculateProgress(Document document) {
        int progress = 0;
        if ("completed".equals(document.getUploadStatus())) progress += 20;
        if ("completed".equals(document.getParseStatus())) progress += 40;
        if ("completed".equals(document.getChunkStatus())) progress += 40;
        return progress;
    }

    private String determineCurrentStep(DocumentParseService.DocumentParseStatus status) {
        if ("failed".equals(status.parseStatus())) return "parse_failed";
        if ("failed".equals(status.chunkStatus())) return "chunk_failed";
        if ("processing".equals(status.parseStatus())) return "parsing";
        if ("completed".equals(status.parseStatus()) && "pending".equals(status.chunkStatus())) {
            return "ready_to_chunk";
        }
        if ("processing".equals(status.chunkStatus())) return "chunking";
        if ("completed".equals(status.parseStatus()) && "completed".equals(status.chunkStatus())) {
            return "completed";
        }
        return "pending";
    }

    private String getUsernameSafely(UserDetails userDetails, String location) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
    }

    private User getUserSafely(UserDetails userDetails, String location) {
        String username = getUsernameSafely(userDetails, location);
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND,
                    String.format("[%s] 用户不存在: %s", location, username));
        }
        return user;
    }

    /**
     * 验证回调URL安全性，防止SSRF攻击
     */
    private void validateCallbackUrl(String callbackUrl) {
        try {
            java.net.URI uri = java.net.URI.create(callbackUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "回调URL仅支持http/https协议");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "回调URL缺少有效主机名");
            }
            // 禁止内网地址
            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "回调URL不允许指向内网地址");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "回调URL格式无效: " + e.getMessage());
        }
    }

    private Map<String, Object> convertToMap(Document document, String currentUsername) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", document.getId());
        map.put("title", document.getTitle());
        map.put("description", document.getDescription());
        map.put("filePath", document.getFilePath());
        map.put("fileName", document.getFileName());
        map.put("fileSize", document.getFileSize());
        map.put("fileType", document.getFileType());
        // 获取文档关联的第一个知识库ID
        String primaryCollectionId = null;
        try {
            var cdList = collectionDocumentMapper.selectList(
                    new QueryWrapper<CollectionDocument>()
                            .eq("document_id", document.getId())
                            .last("LIMIT 1"));
            if (!cdList.isEmpty()) {
                primaryCollectionId = cdList.get(0).getCollectionId();
            }
        } catch (Exception e) {
            log.warn("Failed to get primary collection for document: documentId={}", document.getId());
        }
        map.put("collectionId", primaryCollectionId);
        map.put("ownerId", document.getOwnerId());
        map.put("departmentId", document.getDepartmentId());
        map.put("uploadStatus", document.getUploadStatus());
        map.put("parseStatus", document.getParseStatus());
        map.put("chunkStatus", document.getChunkStatus());
        map.put("chunkCount", document.getChunkCount());
        map.put("expectedChunkCount", document.getExpectedChunkCount());
        map.put("parseErrorMessage", document.getParseErrorMessage());
        map.put("progress", calculateProgress(document));
        map.put("status", document.getStatus());
        map.put("createdAt", document.getCreatedAt());
        map.put("updatedAt", document.getUpdatedAt());

        if (document.getOwner() != null) {
            map.put("ownerName", document.getOwner().getDisplayName());
        } else {
            User owner = userMapper.selectById(document.getOwnerId());
            if (owner != null) {
                map.put("ownerName", owner.getDisplayName());
            }
        }

        // 按用户名查询 userId，用于 isOwner 判断
        User currentUser = userMapper.selectByUsername(currentUsername);
        boolean isOwner = currentUser != null && document.isOwner(currentUser.getId());
        map.put("isOwner", isOwner);

        return map;
    }
}