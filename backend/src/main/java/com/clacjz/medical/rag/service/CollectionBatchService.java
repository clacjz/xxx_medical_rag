package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.util.FileNameUtil;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionBatchService {

    private final DocumentMapper documentMapper;
    private final CollectionMapper collectionMapper;
    private final CollectionDocumentMapper collectionDocumentMapper;
    private final MinioClient minioClient;
    private final RAGProperties ragProperties;
    private final DocumentProcessingService documentProcessingService;
    private final TransactionTemplate transactionTemplate;

    @Async("documentParserExecutor")
    public CompletableFuture<BatchUploadResult> processDocumentAsync(
            String collectionId,
            String documentId,
            String userId) {
        BatchUploadResult result = new BatchUploadResult();
        result.setDocumentId(documentId);

        try {
            Collection collection = collectionMapper.selectById(collectionId);
            if (collection == null) {
                result.setSuccess(false);
                result.setErrorMessage("知识库不存在");
                return CompletableFuture.completedFuture(result);
            }

            Document document = documentMapper.selectById(documentId);
            if (document == null) {
                result.setSuccess(false);
                result.setErrorMessage("文档不存在");
                return CompletableFuture.completedFuture(result);
            }

            updateCollectionDocumentStatus(collectionId, documentId, "PROCESSING", null);

            // 调用统一的文档处理服务（直接调用，不再.get()阻塞，避免双异步线程池死锁）
            String chunkStrategy = extractChunkStrategy(collection.getConfig());
            return documentProcessingService.processDocument(documentId, collectionId, chunkStrategy, true, userId)
                    .thenApply(processResult -> {
                        if (processResult == null || !processResult.isSuccess()) {
                            String errorMsg = processResult != null ? processResult.getErrorMessage() : "处理失败";
                            log.error("异步处理文档失败: documentId={}, error={}", documentId, errorMsg);
                            updateCollectionDocumentStatus(collectionId, documentId, "FAILED", errorMsg);
                            result.setSuccess(false);
                            result.setErrorMessage(errorMsg != null ? errorMsg : "处理失败");
                        } else {
                            updateCollectionDocumentStatus(collectionId, documentId, "COMPLETED", null);
                            document.setChunkStatus("completed");
                            document.setChunkCount(processResult.getStoredCount());
                            documentMapper.updateById(document);

                            result.setSuccess(true);
                            result.setChunkCount(processResult.getStoredCount());
                            result.setCharCount(processResult.getCharCount());
                        }
                        return result;
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
                        String errorMsg = cause != null ? cause.getMessage() : "处理失败";
                        log.error("异步处理文档失败: documentId={}, error={}", documentId, errorMsg);
                        updateCollectionDocumentStatus(collectionId, documentId, "FAILED", errorMsg);
                        result.setSuccess(false);
                        result.setErrorMessage(errorMsg != null ? errorMsg : "处理失败");
                        return result;
                    });

        } catch (Exception e) {
            log.error("异步处理文档失败: documentId={}", documentId, e);
            result.setSuccess(false);
            result.setErrorMessage("处理失败: " + e.getMessage());
            updateCollectionDocumentStatus(collectionId, documentId, "FAILED", e.getMessage());
            return CompletableFuture.completedFuture(result);
        }
    }

    private String extractChunkStrategy(Object config) {
        if (config == null) {
            return "fixed";
        }
        try {
            var method = config.getClass().getMethod("getChunking");
            Object chunking = method.invoke(config);
            if (chunking != null) {
                var strategyMethod = chunking.getClass().getMethod("getStrategy");
                String strategy = (String) strategyMethod.invoke(chunking);
                return strategy != null ? strategy : "fixed";
            }
        } catch (Exception e) {
            log.debug("无法提取分块策略，使用默认: {}", e.getMessage());
        }
        return "fixed";
    }

    /**
     * 更新CollectionDocument和Document状态，使用编程式事务确保在异步回调中也能正确开启事务。
     * （直接调用时@Transactional注解会绕过Spring AOP代理，导致事务不生效）
     */
    public void updateCollectionDocumentStatus(String collectionId, String documentId, String status, String errorMessage) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            CollectionDocument cd = collectionDocumentMapper.selectByCollectionIdAndDocumentId(collectionId, documentId);
            if (cd != null) {
                cd.setProcessingStatus(status);
                if (errorMessage != null) {
                    cd.setProcessingError(errorMessage);
                }
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    cd.setProcessedAt(LocalDateTime.now());
                }
                collectionDocumentMapper.updateById(cd);
            }

            Document document = documentMapper.selectById(documentId);
            if (document != null) {
                if ("COMPLETED".equals(status)) {
                    document.setParseStatus("completed");
                    document.setChunkStatus("completed");
                } else if ("FAILED".equals(status)) {
                    document.setParseStatus("failed");
                    document.setParseErrorMessage(errorMessage);
                } else if ("PROCESSING".equals(status)) {
                    document.setParseStatus("processing");
                }
                documentMapper.updateById(document);
            }
        });
    }

    public BatchUploadResult uploadAndCreateDocument(
            String collectionId,
            MultipartFile file,
            String userId,
            String description,
            String permissionType,
            List<String> allowedUserIds) {

        BatchUploadResult result = new BatchUploadResult();

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            result.setSuccess(false);
            result.setErrorMessage("文件名不能为空");
            return result;
        }

        String sanitizedFilename = FileNameUtil.sanitizeFilename(originalFilename);
        String fileExtension = FileNameUtil.getFileExtension(sanitizedFilename);

        if (!isAllowedFileType(fileExtension)) {
            result.setSuccess(false);
            result.setErrorMessage("不支持的文件类型: " + fileExtension);
            return result;
        }

        if (file.getSize() > 100 * 1024 * 1024) {
            result.setSuccess(false);
            result.setErrorMessage("文件大小不能超过100MB");
            return result;
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

            Document document = new Document();
            document.setId(documentId);
            document.setTitle(sanitizedFilename);
            document.setFileName(sanitizedFilename);
            document.setFilePath(objectName);
            document.setFileType(fileExtension);
            document.setFileSize(file.getSize());
            // documents.collection_id 仅作历史兼容，关联关系通过 collection_documents 表管理
            document.setOwnerId(userId);
            document.setDepartmentId(TenantContext.getDepartmentId());
            document.setUploadStatus("completed");
            document.setParseStatus("pending");
            document.setChunkStatus("pending");
            document.setDescription(description);
            document.setStatus("active");

            documentMapper.insert(document);

            Collection collection = collectionMapper.selectById(collectionId);
            if (collection != null) {
                CollectionDocument cd = CollectionDocument.builder()
                        .collectionId(collectionId)
                        .documentId(documentId)
                        .departmentId(collection.getDepartmentId())
                        .addedBy(userId)
                        .processingStatus("PENDING")
                        .build();
                collectionDocumentMapper.insert(cd);

                collection.incrementDocumentCount();
                collectionMapper.updateById(collection);
            }

            result.setSuccess(true);
            result.setDocumentId(documentId);
            result.setFileName(sanitizedFilename);
            result.setFileSize(file.getSize());

            log.info("批量上传文档成功: documentId={}, collectionId={}, fileName={}",
                    documentId, collectionId, sanitizedFilename);

        } catch (Exception e) {
            log.error("批量上传文档失败: collectionId={}", collectionId, e);
            result.setSuccess(false);
            result.setErrorMessage("文件上传失败: " + e.getMessage());
        }

        return result;
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(bucketName).build()
            );
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

    @lombok.Data
    public static class BatchUploadResult {
        private boolean success;
        private String documentId;
        private String fileName;
        private Long fileSize;
        private Integer chunkCount;
        private Integer charCount;
        private String errorMessage;
    }
}
