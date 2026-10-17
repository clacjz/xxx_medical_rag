package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.CollectionBatchService;
import com.clacjz.medical.rag.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/collections")
@RequiredArgsConstructor
@Tag(name = "Collection API", description = "Collection管理相关接口")
public class CollectionController {

    private final CollectionService collectionService;
    private final CollectionBatchService collectionBatchService;
    private final UserMapper userMapper;

    @GetMapping
    @Operation(summary = "获取Collection列表", description = "分页获取Collection列表，支持按类型、状态、关键词筛选")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PageResult<CollectionDetailDTO>>> listCollections(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "Collection类型") @RequestParam(required = false) CollectionType type,
            @Parameter(description = "Collection状态") @RequestParam(required = false) CollectionStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "listCollections");
        log.info("List collections: page={}, pageSize={}, keyword={}, type={}, status={}, user={}",
                page, pageSize, keyword, type, status, username);

        IPage<CollectionDetailDTO> collections = collectionService.getCollections(page, pageSize, keyword, type, status);
        PageResult<CollectionDetailDTO> result = PageResult.from(collections);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取Collection详情", description = "根据ID获取Collection详细信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionDetailDTO>> getCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getCollection");
        log.info("Get collection: id={}, user={}", id, username);

        CollectionDetailDTO collection = collectionService.getCollectionById(id);
        return ResponseEntity.ok(Result.success(collection));
    }

    @PostMapping
    @Operation(summary = "创建Collection", description = "创建新的Collection")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CollectionDetailDTO>> createCollection(
            @Valid @RequestBody CreateCollectionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "createCollection");
        log.info("Create collection: name={}, type={}, user={}", request.getName(), request.getType(), username);

        CollectionDetailDTO collection = collectionService.createCollection(request);
        return ResponseEntity.ok(Result.success("Collection创建成功", collection));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新Collection", description = "更新Collection信息")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CollectionDetailDTO>> updateCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Valid @RequestBody UpdateCollectionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "updateCollection");
        log.info("Update collection: id={}, user={}", id, username);

        CollectionDetailDTO collection = collectionService.updateCollection(id, request);
        return ResponseEntity.ok(Result.success("Collection更新成功", collection));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除Collection", description = "软删除Collection")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> deleteCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "deleteCollection");
        log.info("Delete collection: id={}, user={}", id, username);

        collectionService.deleteCollection(id);
        return ResponseEntity.ok(Result.success("Collection删除成功", null));
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "归档Collection", description = "将Collection归档，归档后不影响历史访问但不再参与检索")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CollectionDetailDTO>> archiveCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "archiveCollection");
        log.info("Archive collection: id={}, user={}", id, username);

        CollectionDetailDTO collection = collectionService.archiveCollection(id);
        return ResponseEntity.ok(Result.success("Collection归档成功", collection));
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "恢复Collection", description = "将归档的Collection恢复为正常状态")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CollectionDetailDTO>> restoreCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "restoreCollection");
        log.info("Restore collection: id={}, user={}", id, username);

        CollectionDetailDTO collection = collectionService.restoreCollection(id);
        return ResponseEntity.ok(Result.success("Collection恢复成功", collection));
    }

    @GetMapping("/{id}/config")
    @Operation(summary = "获取Collection配置", description = "获取Collection的配置信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionConfig>> getCollectionConfig(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getCollectionConfig");
        log.info("Get collection config: id={}, user={}", id, username);

        CollectionConfig config = collectionService.getCollectionConfig(id);
        return ResponseEntity.ok(Result.success(config));
    }

    @GetMapping("/{id}/statistics")
    @Operation(summary = "获取Collection统计信息", description = "获取Collection的使用统计信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionStatistics>> getCollectionStatistics(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getCollectionStatistics");
        log.info("Get collection statistics: id={}, user={}", id, username);

        CollectionStatistics statistics = collectionService.getCollectionStatistics(id);
        return ResponseEntity.ok(Result.success(statistics));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "获取Collection指标", description = "获取Collection的使用指标(查询量、延迟、错误率等)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionStatistics>> getCollectionMetrics(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails, "getCollectionMetrics");
        log.info("Get collection metrics: id={}, user={}", id, username);
        CollectionStatistics statistics = collectionService.getCollectionStatistics(id);
        return ResponseEntity.ok(Result.success(statistics));
    }

    @PutMapping("/{id}/config")
    @Operation(summary = "更新Collection配置", description = "更新Collection的检索、生成、分块配置")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CollectionDetailDTO>> updateCollectionConfig(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Valid @RequestBody UpdateCollectionConfigRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "updateCollectionConfig");
        log.info("Update collection config: id={}, user={}", id, username);

        CollectionDetailDTO collection = collectionService.updateCollectionConfig(id, request.getConfig());
        return ResponseEntity.ok(Result.success("配置更新成功", collection));
    }

    @PostMapping("/{id}/documents")
    @Operation(summary = "添加文档到Collection", description = "将文档关联到Collection")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> addDocumentsToCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Valid @RequestBody AddDocumentsToCollectionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "addDocumentsToCollection");
        log.info("Add documents to collection: id={}, count={}, user={}", id, request.getDocumentIds().size(), username);

        collectionService.addDocumentsToCollection(id, request);
        return ResponseEntity.ok(Result.success("文档添加成功", null));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @Operation(summary = "从Collection移除文档", description = "将文档从Collection中移除")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> removeDocumentFromCollection(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "removeDocumentFromCollection");
        log.info("Remove document from collection: collectionId={}, documentId={}, user={}", id, documentId, username);

        collectionService.removeDocumentFromCollection(id, documentId);
        return ResponseEntity.ok(Result.success("文档移除成功", null));
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "获取Collection文档列表", description = "获取Collection关联的文档列表")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PageResult<CollectionDocumentDTO>>> getCollectionDocuments(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "处理状态") @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getCollectionDocuments");
        log.info("Get collection documents: id={}, page={}, pageSize={}, status={}, user={}",
                id, page, pageSize, status, username);

        IPage<CollectionDocumentDTO> documents = collectionService.getCollectionDocuments(id, page, pageSize, status);
        PageResult<CollectionDocumentDTO> result = PageResult.from(documents);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量上传文档到Collection", description = "支持多文件批量上传，自动创建文档记录并可选立即处理")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN', 'DEPT_UPLOADER')")
    public ResponseEntity<Result<BatchUploadResponse>> batchUploadDocuments(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Parameter(description = "上传的文件列表") @RequestParam("files") MultipartFile[] files,
            @Parameter(description = "是否立即处理") @RequestParam(defaultValue = "false") boolean autoProcess,
            @Parameter(description = "分块策略") @RequestParam(defaultValue = "fixed") String strategy,
            @Parameter(description = "权限类型") @RequestParam(required = false) String permissionType,
            @Parameter(description = "描述") @RequestParam(required = false) String description,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "batchUploadDocuments");
        log.info("Batch upload documents: collectionId={}, fileCount={}, autoProcess={}, user={}",
                id, files.length, autoProcess, user.getUsername());

        if (files == null || files.length == 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "请选择要上传的文件");
        }

        if (files.length > 50) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "单次上传文件数不能超过50个");
        }

        List<CollectionBatchService.BatchUploadResult> results = new ArrayList<>();
        List<String> successDocIds = new ArrayList<>();

        for (MultipartFile file : files) {
            CollectionBatchService.BatchUploadResult result = collectionBatchService.uploadAndCreateDocument(
                    id, file, user.getId(), description, permissionType, null);

            results.add(result);

            if (result.isSuccess()) {
                successDocIds.add(result.getDocumentId());

                if (autoProcess) {
                    collectionBatchService.processDocumentAsync(id, result.getDocumentId(), user.getId());
                    log.info("文档已加入处理队列: documentId={}", result.getDocumentId());
                }
            }
        }

        int successCount = (int) results.stream().filter(CollectionBatchService.BatchUploadResult::isSuccess).count();
        int failedCount = results.size() - successCount;

        List<BatchUploadResponse.CollectionBatchUploadResult> uploadResults = results.stream()
                .map(r -> BatchUploadResponse.CollectionBatchUploadResult.builder()
                        .success(r.isSuccess())
                        .documentId(r.getDocumentId())
                        .fileName(r.getFileName())
                        .fileSize(r.getFileSize())
                        .chunkCount(r.getChunkCount())
                        .charCount(r.getCharCount())
                        .errorMessage(r.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        BatchUploadResponse response = new BatchUploadResponse();
        response.setTotalFiles(files.length);
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setAutoProcess(autoProcess);
        response.setResults(uploadResults);

        String message = String.format("上传完成: 成功 %d 个, 失败 %d 个", successCount, failedCount);
        if (autoProcess) {
            message += " (文档已加入处理队列)";
        }

        log.info("批量上传完成: collectionId={}, success={}, failed={}", id, successCount, failedCount);

        return ResponseEntity.ok(Result.success(message, response));
    }

    @GetMapping("/{id}/documents/{documentId}/status")
    @Operation(summary = "获取文档处理状态", description = "查询指定文档的处理状态")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> getDocumentProcessStatus(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getDocumentProcessStatus");
        log.info("Get document status: collectionId={}, documentId={}, user={}", id, documentId, username);

        Map<String, Object> status = collectionService.getDocumentProcessStatus(id, documentId);
        return ResponseEntity.ok(Result.success(status));
    }

    @PostMapping("/{id}/documents/{documentId}/process")
    @Operation(summary = "手动触发文档处理", description = "对已上传的文档进行解析和向量化")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN', 'DEPT_UPLOADER')")
    public ResponseEntity<Result<Map<String, Object>>> triggerDocumentProcess(
            @Parameter(description = "Collection ID") @PathVariable String id,
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Parameter(description = "分块策略") @RequestParam(defaultValue = "fixed") String strategy,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserSafely(userDetails, "triggerDocumentProcess");
        log.info("Trigger document process: collectionId={}, documentId={}, strategy={}, user={}",
                id, documentId, strategy, user.getUsername());

        var result = collectionBatchService.processDocumentAsync(id, documentId, user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("status", "processing");
        response.put("message", "文档处理任务已提交");

        return ResponseEntity.ok(Result.success("文档处理已提交", response));
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
}
