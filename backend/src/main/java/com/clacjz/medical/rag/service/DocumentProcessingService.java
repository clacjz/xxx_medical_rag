package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 统一文档处理服务
 * 封装文档解析、分块、向量化存储的完整流程，供DocumentController和CollectionBatchService共用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentMapper documentMapper;
    private final CollectionMapper collectionMapper;
    private final DocumentParseService documentParseService;
    private final DocumentVectorizationService documentVectorizationService;
    private final UserMapper userMapper;
    private final CollectionDocumentMapper collectionDocumentMapper;

    /**
     * 统一文档处理入口
     *
     * @param documentId 文档ID
     * @param collectionId 目标知识库ID（可选，为空时使用文档关联的知识库）
     * @param strategy 分块策略：fixed/paragraph/semantic
     * @param enableOcrFallback PDF文本量过少时是否启用OCR回退
     * @param userId 处理人用户ID（用于更新DocumentPermission）
     * @return 处理结果，包含存储的向量数量
     */
    @Async("documentParserExecutor")
    public CompletableFuture<ProcessResult> processDocument(
            String documentId,
            String collectionId,
            String strategy,
            boolean enableOcrFallback,
            String userId) {

        ProcessResult result = new ProcessResult();
        result.setDocumentId(documentId);
        // 在 try 块外声明，确保 catch 块也能访问
        String targetCollectionId = null;

        try {
            // 1. 解析文档
            DocumentParseService.DocumentParseResult parseResult =
                    documentParseService.parseDocument(documentId, strategy, enableOcrFallback);

            if (!"completed".equals(parseResult.status())) {
                result.setSuccess(false);
                result.setErrorMessage(parseResult.errorMessage());
                return CompletableFuture.completedFuture(result);
            }

            // 2. 获取文档信息
            Document document = documentMapper.selectById(documentId);
            if (document == null) {
                result.setSuccess(false);
                result.setErrorMessage("文档不存在");
                return CompletableFuture.completedFuture(result);
            }

            // 3. 确定目标知识库（优先使用参数，其次从 collection_documents 关联表查找）
            targetCollectionId = collectionId;
            if (targetCollectionId == null || targetCollectionId.isBlank()) {
                // 从 collection_documents 关联表中查找该文档关联的第一个知识库
                var cdList = collectionDocumentMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollectionDocument>()
                                .eq("document_id", documentId)
                                .last("LIMIT 1"));
                if (!cdList.isEmpty()) {
                    targetCollectionId = cdList.get(0).getCollectionId();
                }
            }
            if (targetCollectionId == null || targetCollectionId.isBlank()) {
                result.setSuccess(false);
                result.setErrorMessage("请指定知识库ID，文档尚未关联到任何知识库");
                return CompletableFuture.completedFuture(result);
            }

            // 4. 向量化存储
            int storedCount = documentVectorizationService.vectorizeMarkdown(
                    parseResult.originalText(),
                    targetCollectionId,
                    documentId,
                    document.getTitle(),
                    strategy,
                    parseResult.docType() != null ? parseResult.docType()
                            : com.clacjz.medical.rag.model.enums.DocType.NORMAL,
                    parseResult.keySections(),
                    parseResult.pageAnnotations()
            );

            // 5. 检查文档在向量化期间是否已被删除
            Document docAfterVectorization = documentMapper.selectById(documentId);
            if (docAfterVectorization == null) {
                log.warn("DocumentProcessingService: 文档在向量化期间已被删除，跳过状态更新: documentId={}", documentId);
                result.setSuccess(false);
                result.setErrorMessage("文档在向量化期间已被删除");
                return CompletableFuture.completedFuture(result);
            }

            // 6. 更新文档状态
            docAfterVectorization.setParseStatus("completed");
            docAfterVectorization.setChunkStatus("completed");
            docAfterVectorization.setChunkCount(storedCount);
            documentMapper.updateById(docAfterVectorization);

            // 7. 更新CollectionDocument的处理状态
            updateCollectionDocumentStatus(targetCollectionId, documentId, "COMPLETED", null);

            result.setSuccess(true);
            result.setStoredCount(storedCount);
            result.setCharCount(parseResult.charCount());
            result.setParseResult(parseResult);

            log.info("DocumentProcessingService: 文档处理完成: documentId={}, storedCount={}",
                    documentId, storedCount);

        } catch (BizException e) {
            log.error("DocumentProcessingService: 文档处理失败[业务异常]: documentId={}", documentId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            // 更新CollectionDocument状态为失败（使用已解析的 targetCollectionId）
            if (targetCollectionId != null && !targetCollectionId.isBlank()) {
                updateCollectionDocumentStatus(targetCollectionId, documentId, "FAILED", e.getMessage());
            }
        } catch (Exception e) {
            log.error("DocumentProcessingService: 文档处理失败[系统异常]: documentId={}", documentId, e);
            result.setSuccess(false);
            result.setErrorMessage("处理失败: " + e.getMessage());
            // 更新CollectionDocument状态为失败（使用已解析的 targetCollectionId）
            if (targetCollectionId != null && !targetCollectionId.isBlank()) {
                updateCollectionDocumentStatus(targetCollectionId, documentId, "FAILED", e.getMessage());
            }
        }

        return CompletableFuture.completedFuture(result);
    }

    @lombok.Data
    public static class ProcessResult {
        private boolean success;
        private String documentId;
        private Integer storedCount;
        private Integer charCount;
        private String errorMessage;
        private DocumentParseService.DocumentParseResult parseResult;
    }

    /**
     * 更新CollectionDocument的处理状态
     */
    private void updateCollectionDocumentStatus(String collectionId, String documentId, String status, String errorMessage) {
        try {
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
                log.info("更新CollectionDocument状态: collectionId={}, documentId={}, status={}", collectionId, documentId, status);
            }
        } catch (Exception e) {
            log.error("更新CollectionDocument状态失败: collectionId={}, documentId={}", collectionId, documentId, e);
        }
    }
}
