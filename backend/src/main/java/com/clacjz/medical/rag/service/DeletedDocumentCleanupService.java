package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Document;
import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 软删除补偿任务服务
 * 定期清理已删除文档的关联数据（向量、物理文件等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedDocumentCleanupService {

    private final DocumentMapper documentMapper;
    private final CollectionMapper collectionMapper;
    private final CollectionDocumentMapper collectionDocumentMapper;
    private final VectorCrudService vectorCrudService;
    private final EsIndexService esIndexService;
    private final TempFileCleanupService tempFileCleanupService;
    private final QdrantClient qdrantClient;

    /**
     * 每日凌晨3点执行清理任务
     * 延迟7天后物理删除已软删除的文档
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupDeletedDocuments() {
        log.info("[Compensation Task] Starting deleted document cleanup");

        // 查询7天前已软删除的文档
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        List<Document> deletedDocuments = documentMapper.selectDeletedDocuments();

        if (deletedDocuments.isEmpty()) {
            log.info("[Compensation Task] No deleted documents to clean up");
            return;
        }

        log.info("[Compensation Task] Found {} deleted documents pending cleanup", deletedDocuments.size());

        int successCount = 0;
        int failCount = 0;

        for (Document document : deletedDocuments) {
            try {
                // 检查删除时间是否已超过7天
                if (document.getUpdatedAt() != null && document.getUpdatedAt().isAfter(cutoffDate)) {
                    log.debug("Skipping document {} - deleted within 7 days", document.getId());
                    continue;
                }

                // 1. 从 collection_documents 关联表获取该文档关联的所有知识库
                var relatedCDs = collectionDocumentMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollectionDocument>()
                                .eq("document_id", document.getId()));

                for (CollectionDocument cd : relatedCDs) {
                    try {
                        vectorCrudService.deleteByDocumentId(cd.getCollectionId(), document.getId());
                        log.info("[Compensation Task] Deleted vectors: collectionId={}, documentId={}",
                                cd.getCollectionId(), document.getId());
                    } catch (Exception e) {
                        log.warn("[Compensation Task] Failed to delete vectors: documentId={}, collectionId={}, error={}",
                                document.getId(), cd.getCollectionId(), e.getMessage());
                    }
                    try {
                        esIndexService.deleteDocument(cd.getCollectionId(), document.getId());
                        log.info("[Compensation Task] Deleted ES index: collectionId={}, documentId={}",
                                cd.getCollectionId(), document.getId());
                    } catch (Exception e) {
                        log.warn("[Compensation Task] Failed to delete ES index: documentId={}, collectionId={}, error={}",
                                document.getId(), cd.getCollectionId(), e.getMessage());
                    }
                }

                // 2. 清理临时文件
                if (document.getProcessResultFile() != null) {
                    tempFileCleanupService.cleanupFile(document.getProcessResultFile());
                }

                // 4. 物理删除文档记录
                documentMapper.deleteById(document.getId());
                log.info("[Compensation Task] Physically deleted document: {}", document.getId());

                successCount++;

            } catch (Exception e) {
                log.error("[Compensation Task] Failed to cleanup document: {}", document.getId(), e);
                failCount++;
            }
        }

        log.info("[Compensation Task] Cleanup completed: success={}, failed={}", successCount, failCount);
    }

    /**
     * 清理孤立向量（Qdrant中有但Document已不存在的向量）
     * 每周执行一次
     */
    @Scheduled(cron = "0 30 3 * * SUN")
    public void cleanupOrphanVectors() {
        log.info("[Compensation Task] Starting orphan vector cleanup");

        List<Collection> collections = collectionMapper.selectAllActive();

        for (Collection collection : collections) {
            try {
                String collectionName = collection.getName();
                long pointCount = vectorCrudService.getCollectionPointCount(collectionName);

                if (pointCount == 0) {
                    log.debug("[Compensation Task] Collection {} has no vectors, skip", collectionName);
                    continue;
                }

                log.info("[Compensation Task] Checking collection: {}, pointCount={}", collectionName, pointCount);

                // 1. 获取该collection关联的有效文档ID列表
                var collectionDocs = collectionDocumentMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollectionDocument>()
                                .eq("collection_id", collection.getId()));
                Set<String> validDocIds = new HashSet<>();
                for (CollectionDocument cd : collectionDocs) {
                    validDocIds.add(cd.getDocumentId());
                }

                if (validDocIds.isEmpty()) {
                    log.debug("[Compensation Task] Collection {} has no valid documents", collectionName);
                    continue;
                }

                // 2. 扫描Qdrant中的所有document_id
                Set<String> qdrantDocIds = vectorCrudService.scrollAllPayloadValues(collectionName, "document_id");
                log.info("[Compensation Task] Collection {} found {} unique document_ids from Qdrant",
                        collectionName, qdrantDocIds.size());

                // 3. 对比找出孤立向量
                Set<String> orphanDocIds = new HashSet<>(qdrantDocIds);
                orphanDocIds.removeAll(validDocIds);

                if (orphanDocIds.isEmpty()) {
                    log.info("[Compensation Task] Collection {} has no orphan vectors", collectionName);
                    continue;
                }

                log.info("[Compensation Task] Collection {} has {} orphan document_ids to clean up",
                        collectionName, orphanDocIds.size());

                // 4. 删除孤立向量
                int deletedCount = 0;
                for (String orphanDocId : orphanDocIds) {
                    try {
                        vectorCrudService.deleteByDocumentId(collectionName, orphanDocId);
                        deletedCount++;
                    } catch (Exception e) {
                        log.warn("[Compensation Task] Failed to delete orphan vectors: docId={}, error={}",
                                orphanDocId, e.getMessage());
                    }
                }

                log.info("[Compensation Task] Collection {} cleaned {} orphan vectors", collectionName, deletedCount);

            } catch (Exception e) {
                log.error("[Compensation Task] Failed to cleanup collection: {}", collection.getName(), e);
            }
        }

        log.info("[Compensation Task] Orphan vector cleanup completed");
    }

    /**
     * 清理孤立向量后，调用Qdrant optimize回收空间
     * 每周执行一次，在孤立向量清理之后
     */
    @Scheduled(cron = "0 45 3 * * SUN")
    public void optimizeCollections() {
        log.info("[Compensation Task] Starting collection optimization");

        List<Collection> collections = collectionMapper.selectAllActive();

        for (Collection collection : collections) {
            try {
                String collectionName = collection.getName();
                long pointCount = vectorCrudService.getCollectionPointCount(collectionName);

                if (pointCount == 0) {
                    continue;
                }

                log.info("[Compensation Task] Optimizing collection: {}, pointCount={}", collectionName, pointCount);

                // 调用updateCollection设置optimizers配置
                // deleted_threshold=0.1 表示当10%以上的点被标记删除时触发GC
                var updateRequest = io.qdrant.client.grpc.Collections.UpdateCollection.newBuilder()
                        .setCollectionName(collectionName)
                        .setOptimizersConfig(
                                io.qdrant.client.grpc.Collections.OptimizersConfigDiff.newBuilder()
                                        .setDeletedThreshold(0.1f)
                                        .build())
                        .build();

                qdrantClient.updateCollectionAsync(updateRequest).get(30, TimeUnit.SECONDS);

                log.info("[Compensation Task] Collection {} optimization triggered", collectionName);

            } catch (Exception e) {
                log.warn("[Compensation Task] Failed to optimize collection: {}, error={}",
                        collection.getName(), e.getMessage());
            }
        }

        log.info("[Compensation Task] Collection optimization completed");
    }
}