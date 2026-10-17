package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.DocumentPermissionMapper;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.DocumentPermission;
import com.clacjz.medical.rag.model.enums.DocType;
import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
import com.clacjz.medical.rag.service.chunking.SemanticChunkingService;
import com.clacjz.medical.rag.service.chunking.TextChunker;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 文档向量化服务
 * 负责将文本内容分块、向量化并存储到Qdrant
 * 同时支持将文档内容索引到Elasticsearch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVectorizationService {

    private final TextChunker textChunker;
    private final SemanticChunkingService semanticChunkingService;
    private final EmbeddingService embeddingService;
    private final VectorCrudService vectorCrudService;
    private final CollectionMapper collectionMapper;
    private final CollectionManagementService collectionManagementService;
    private final DocumentMapper documentMapper;
    private final DocumentPermissionMapper documentPermissionMapper;
    private final EsIndexService esIndexService;
    private final DocumentChunkService documentChunkService;

    @Resource(name = "embeddingExecutor")
    private Executor embeddingExecutor;

    /**
     * 将markdown文本向量化并存储到Qdrant
     *
     * @param markdownText markdown文本内容
     * @param collectionId 知识库ID（对应Qdrant collection name）
     * @param documentId 文档ID（用于追踪来源）
     * @param title 文档标题
     * @return 存储的向量点数量
     */
    public int vectorizeMarkdown(String markdownText, String collectionId, String documentId, String title) {
        return vectorizeMarkdown(markdownText, collectionId, documentId, title, "fixed", DocType.NORMAL, null, null);
    }

    /**
     * 将markdown文本向量化并存储到Qdrant
     *
     * @param markdownText markdown文本内容
     * @param collectionId 知识库ID（对应Qdrant collection name）
     * @param documentId 文档ID（用于追踪来源）
     * @param title 文档标题
     * @param strategy 分块策略：fixed/paragraph/semantic
     * @param docType 文档类型：MANUAL/NORMAL
     * @param keySections 结构化字段（仅MANUAL类型）
     * @param pageAnnotations 页面标注（仅MANUAL类型）
     * @return 存储的向量点数量
     */
    public int vectorizeMarkdown(String markdownText, String collectionId, String documentId, String title,
                                 String strategy) {
        return vectorizeMarkdown(markdownText, collectionId, documentId, title, strategy, DocType.NORMAL, null, null);
    }

    /**
     * 将markdown文本向量化并存储到Qdrant
     *
     * @param markdownText markdown文本内容
     * @param collectionId 知识库ID（对应Qdrant collection name）
     * @param documentId 文档ID（用于追踪来源）
     * @param title 文档标题
     * @param strategy 分块策略：fixed/paragraph/semantic
     * @param docType 文档类型：MANUAL/NORMAL
     * @param keySections 结构化字段（仅MANUAL类型）
     * @param pageAnnotations 页面标注（仅MANUAL类型）
     * @return 存储的向量点数量
     */
    public int vectorizeMarkdown(String markdownText, String collectionId, String documentId, String title,
                                 String strategy, DocType docType,
                                 Map<String, Object> keySections,
                                 List<Document.PageAnnotation> pageAnnotations) {
        log.info("开始向量化文档: collectionId={}, documentId={}, title={}, strategy={}, docType={}, textLength={}",
                collectionId, documentId, title, strategy, docType, markdownText.length());

        // 1. 确保Qdrant collection存在
        ensureCollectionExists(collectionId);

        // 2. 根据docType决定处理方式
        int storedCount;
        if (docType == DocType.MANUAL) {
            // MANUAL类型：不切片，整篇作为1个chunk
            storedCount = vectorizeManualDocument(markdownText, collectionId, documentId, title,
                    keySections, pageAnnotations);
        } else {
            // NORMAL类型：正常分块
            storedCount = vectorizeNormalDocument(markdownText, collectionId, documentId, title, strategy, keySections);
        }

        // 3. 同步写入ES（异步补偿任务后续处理失败重试）
        try {
            indexToEs(markdownText, collectionId, documentId, title, docType, keySections, pageAnnotations);
        } catch (Exception e) {
            log.error("ES索引写入失败，将通过补偿任务重试: documentId={}, error={}", documentId, e.getMessage());
            // 将ES失败信息记录到文档状态，让用户可见而非静默忽略
            try {
                Document doc = documentMapper.selectById(documentId);
                if (doc != null) {
                    String existingError = doc.getParseErrorMessage();
                    String esWarning = "ES索引失败，全文搜索可能不完整: " + e.getMessage();
                    doc.setParseErrorMessage(existingError != null ? existingError + "; " + esWarning : esWarning);
                    documentMapper.updateById(doc);
                }
            } catch (Exception ignored) {
                log.warn("记录ES索引失败状态时出错，忽略: documentId={}", documentId, ignored);
            }
        }

        log.info("文档向量化完成: collectionId={}, documentId={}, stored={}", collectionId, documentId, storedCount);

        return storedCount;
    }

    /**
     * 向量化MANUAL类型文档（说明书类）
     * 整篇作为1个chunk存储，但超过阈值时先分块避免embedding截断
     */
    private int vectorizeManualDocument(String fullText, String collectionId, String documentId, String title,
                                        Map<String, Object> keySections,
                                        List<Document.PageAnnotation> pageAnnotations) {
        log.info("[Vectorize:MANUAL] Starting, documentId={}", documentId);

        try {
            // 预查询Document和Permission，避免N+1查询
            Document document = documentMapper.selectById(documentId);
            DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);

            // 超过阈值时先分块，避免embedding模型截断（bge-large-zh-v1.5最大512 tokens）
            int maxChars = 2000; // 保守阈值：2000字符≈400-500 tokens，留有裕量
            List<String> textChunks;
            if (fullText.length() > maxChars) {
                log.info("[Vectorize:MANUAL] Text length={} > {}, splitting into chunks", fullText.length(), maxChars);
                textChunks = splitTextIntoChunks(fullText, maxChars);
            } else {
                textChunks = List.of(fullText);
            }

            // 并行向量化并存储每个chunk
            Map<Integer, String> pointIdIndexMap = new ConcurrentHashMap<>();
            AtomicInteger storedCounter = new AtomicInteger(0);
            AtomicInteger failCounter = new AtomicInteger(0);
            List<String> failedChunkErrors = Collections.synchronizedList(new ArrayList<>());

            List<CompletableFuture<Void>> futures = new ArrayList<>(textChunks.size());
            for (int i = 0; i < textChunks.size(); i++) {
                final int index = i;
                String chunkText = textChunks.get(i);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        float[] vector = embeddingService.embed(chunkText);
                        if (vector == null || vector.length == 0) {
                            log.error("向量化返回空向量: documentId={}, chunkIndex={}", documentId, index);
                            failCounter.incrementAndGet();
                            failedChunkErrors.add("chunk " + index + ": embedding返回空向量");
                            return;
                        }
                        Map<String, Object> payload = buildManualPayload(documentId, title, chunkText, keySections, pageAnnotations, document, permission, index);
                        String pointId = UUID.randomUUID().toString();
                        vectorCrudService.upsertVector(collectionId, vector, payload, pointId);
                        pointIdIndexMap.put(index, pointId);
                        storedCounter.incrementAndGet();
                    } catch (Exception e) {
                        failCounter.incrementAndGet();
                        failedChunkErrors.add("chunk " + index + ": " + e.getMessage());
                        log.error("Chunk {} 存储失败: {}", index, e.getMessage());
                    }
                }, embeddingExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 如果有chunk失败，记录并抛出异常让调用方感知
            if (failCounter.get() > 0) {
                String errorMsg = String.format("MANUAL文档部分chunk向量化失败: %d/%d chunks失败. 错误: %s",
                        failCounter.get(), textChunks.size(),
                        failedChunkErrors.stream().limit(3).collect(Collectors.joining("; ")));
                log.error("[Vectorize:MANUAL] documentId={}, {}", documentId, errorMsg);
                if (storedCounter.get() == 0) {
                    throw new RuntimeException(errorMsg);
                }
                // 部分成功：记录警告但继续
                log.warn("[Vectorize:MANUAL] documentId={}, partial failure: {}", documentId, errorMsg);
            }

            // 按原始顺序重建列表
            int storedCount = storedCounter.get();
            List<String> successfulPointIds = new ArrayList<>(storedCount);
            List<TextChunker.Chunk> successfulChunks = new ArrayList<>(storedCount);
            int chunkStartPos = 0;
            for (int i = 0; i < textChunks.size(); i++) {
                String pointId = pointIdIndexMap.get(i);
                if (pointId != null) {
                    String chunkText = textChunks.get(i);
                    successfulPointIds.add(pointId);
                    // 构建TextChunker.Chunk记录，startPage/endPage为0（MANUAL文档暂无页码信息）
                    successfulChunks.add(new TextChunker.Chunk(i, chunkText, chunkStartPos, chunkStartPos + chunkText.length(), 0, 0));
                    chunkStartPos += chunkText.length();
                }
            }

            // 同步保存分块元数据到数据库
            if (!successfulChunks.isEmpty()) {
                try {
                    documentChunkService.saveChunks(documentId, successfulChunks, successfulPointIds);
                } catch (Exception e) {
                    log.error("保存分块元数据失败: documentId={}, chunkCount={}, errorClass={}, message={}",
                            documentId, successfulChunks.size(), e.getClass().getName(),
                            e.getMessage() != null ? e.getMessage() : "null exception message", e);
                }
            }

            // 更新Collection的chunk计数
            updateCollectionChunkCount(collectionId, successfulPointIds.size());

            log.info("[Vectorize:MANUAL] Completed, documentId={}, storedCount={}", documentId, successfulPointIds.size());
            return successfulPointIds.size();

        } catch (Exception e) {
            log.error("[Vectorize:MANUAL] Failed, documentId={}, error={}", documentId, e.getMessage());
            throw new RuntimeException("MANUAL文档向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将长文本分割为多个子块（用于避免embedding截断）
     */
    private List<String> splitTextIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int startPos = 0;
        while (startPos < text.length()) {
            int endPos = Math.min(startPos + maxChars, text.length());
            if (endPos < text.length()) {
                int lastPeriod = text.lastIndexOf("。", endPos);
                int lastNewline = text.lastIndexOf("\n", endPos);
                int lastSpace = text.lastIndexOf(" ", endPos);
                int breakPoint = Math.max(lastPeriod, Math.max(lastNewline, lastSpace));
                if (breakPoint > startPos + maxChars / 2) {
                    endPos = breakPoint + 1;
                }
            }
            String chunk = text.substring(startPos, endPos);
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (endPos >= text.length()) {
                break;
            }
            startPos = endPos;
        }
        return chunks;
    }

    /**
     * 向量化NORMAL类型文档（普通文档）
     */
    private int vectorizeNormalDocument(String markdownText, String collectionId, String documentId, String title,
                                        String strategy, Map<String, Object> keySections) {
        log.info("[Vectorize:NORMAL] Starting, documentId={}, strategy={}, hasKeySections={}",
                documentId, strategy, keySections != null && !keySections.isEmpty());

        // 文本分块
        List<TextChunker.Chunk> chunks;
        if ("semantic".equalsIgnoreCase(strategy)) {
            // 语义分块
            SemanticChunkingService.SemanticChunkResult semanticResult =
                    semanticChunkingService.semanticChunk(markdownText, 10);
            int[] index = {0};
            chunks = semanticResult.getChunks().stream()
                    .map(c -> new TextChunker.Chunk(
                            index[0]++,
                            c.getOriginal(),
                            c.getStartPos(),
                            c.getEndPos(),
                            0, 0,  // 语义分块暂无页码信息
                            c.getSummary()
                    ))
                    .toList();
        } else if ("paragraph".equalsIgnoreCase(strategy)) {
            chunks = textChunker.chunkByParagraph(markdownText);
        } else {
            chunks = textChunker.chunkFixedSize(markdownText);
        }

        log.info("[Vectorize:NORMAL] Chunking done, chunkCount={}", chunks.size());

        // 预查询Document和Permission，避免N+1查询
        Document document = documentMapper.selectById(documentId);
        DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);

        // 并行向量化并存储每个chunk（使用 ConcurrentHashMap 按 chunk.index 收集，无需锁）
        Map<Integer, String> pointIdMap = new ConcurrentHashMap<>();
        Map<Integer, TextChunker.Chunk> chunkMap = new ConcurrentHashMap<>();
        AtomicInteger storedCounter = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(() -> {
                    try {
                        float[] vector = embeddingService.embed(chunk.text());
                        if (vector == null || vector.length == 0) {
                            log.error("向量化返回空向量: documentId={}, chunkIndex={}", documentId, chunk.index());
                            return;
                        }

                        Map<String, Object> payload = buildPayloadWithPermission(documentId, title, chunk, document, permission, keySections);

                        String pointId = UUID.randomUUID().toString();
                        vectorCrudService.upsertVector(collectionId, vector, payload, pointId);
                        pointIdMap.put(chunk.index(), pointId);
                        chunkMap.put(chunk.index(), chunk);
                        storedCounter.incrementAndGet();

                    } catch (Exception e) {
                        log.error("Chunk {} 存储失败: {}", chunk.index(), e.getMessage());
                    }
                }, embeddingExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 按原始 chunks 顺序重建有序列表（确保 saveChunks 的 pointIds 与 chunks 一一对应）
        int storedCount = storedCounter.get();
        List<String> pointIds = new ArrayList<>(storedCount);
        List<TextChunker.Chunk> successfulChunks = new ArrayList<>(storedCount);
        for (TextChunker.Chunk chunk : chunks) {
            String pointId = pointIdMap.get(chunk.index());
            if (pointId != null) {
                pointIds.add(pointId);
                successfulChunks.add(chunkMap.get(chunk.index()));
            }
        }

        // 同步保存分块元数据到数据库
        if (!successfulChunks.isEmpty()) {
            try {
                documentChunkService.saveChunks(documentId, successfulChunks, pointIds);
            } catch (Exception e) {
                log.error("保存分块元数据失败: documentId={}, chunkCount={}, errorClass={}, message={}",
                        documentId, successfulChunks.size(), e.getClass().getName(),
                        e.getMessage() != null ? e.getMessage() : "null exception message", e);
                // 注意：向量化本身可能已成功，只是元数据保存失败
                // 这种情况需要手动修复或重新处理文档
            }
        }

        // 更新Collection的chunk计数
        if (storedCount > 0) {
            updateCollectionChunkCount(collectionId, storedCount);
        }

        log.info("[Vectorize:NORMAL] Completed, documentId={}, storedCount={}", documentId, storedCount);
        return storedCount;
    }

    /**
     * 构建MANUAL类型的payload
     */
    private Map<String, Object> buildManualPayload(String documentId, String title, String fullText,
                                                     Map<String, Object> keySections,
                                                     List<Document.PageAnnotation> pageAnnotations,
                                                     Document document, DocumentPermission permission,
                                                     int chunkIndex) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("document_id", documentId);
        payload.put("title", title);
        payload.put("text", fullText);
        payload.put("chunk_index", chunkIndex);
        payload.put("content_length", fullText.length());
        payload.put("doc_type", DocType.MANUAL.name());

        // key_sections（完整结构化数据，供数据库展示）
        if (keySections != null && !keySections.isEmpty()) {
            payload.put("key_sections", keySections);

            // 提取filter字段到payload顶层（用于Qdrant payload filter）
            extractStringField(payload, keySections, "brandName");
            extractStringField(payload, keySections, "genericName");
            extractStringField(payload, keySections, "executionStandard");
            extractStringField(payload, keySections, "marketingAuthorizationHolder");

            // 适应症NER结果（二次LLM提取的疾病和症状）
            extractListField(payload, keySections, "_diseases", "diseases");
            extractListField(payload, keySections, "_symptoms", "symptoms");

            // 适应症原文提升到payload顶层（用于Qdrant keyword filter和ES multi_match）
            extractStringField(payload, keySections, "indications");

            // 同义词指引：适应症↔功能主治等，配合检索侧 TermNormalizer 使用
            payload.put("indication_terms", List.of("适应症", "功能主治", "主治"));
        }

        // 注入权限元数据
        injectPermissionMetadata(payload, document, permission);

        return payload;
    }

    /**
     * 注入权限元数据到payload（使用预查询的Document和Permission，避免N+1查询）
     */
    private void injectPermissionMetadata(Map<String, Object> payload, Document document, DocumentPermission permission) {
        if (permission != null) {
            payload.put("permission_type", permission.getPermissionType().name());
            payload.put("department_ids", permission.getDepartmentIdList());
            payload.put("direct_user_ids", permission.getDirectUserIdList());
            payload.put("owner_id", document != null ? document.getOwnerId() : null);
            payload.put("collection_id", null); // 多对多关系，不再写入单一 collection_id
        } else {
            payload.put("permission_type", PermissionTypeEnums.DEPARTMENT_WIDE.name());
            payload.put("department_ids", document != null ? document.getDepartmentIdList() : Collections.emptyList());
            payload.put("direct_user_ids", Collections.emptyList());
            payload.put("owner_id", document != null ? document.getOwnerId() : null);
            payload.put("collection_id", null); // 多对多关系，不再写入单一 collection_id
        }
    }

    /**
     * 索引文档到Elasticsearch
     */
    private void indexToEs(String fullText, String collectionId, String documentId, String title,
                           DocType docType, Map<String, Object> keySections,
                           List<Document.PageAnnotation> pageAnnotations) {
        try {
            Document document = documentMapper.selectById(documentId);

            EsIndexService.DocumentIndexRequest request = EsIndexService.DocumentIndexRequest.builder()
                    .documentId(documentId)
                    .collectionId(collectionId)
                    .title(title)
                    .docType(docType)
                    .fileType(document != null ? document.getFileType() : null)
                    .fullText(fullText)
                    .keySections(keySections)
                    .pageAnnotations(pageAnnotations != null ? pageAnnotations.stream()
                            .map(pa -> EsIndexService.DocumentIndexRequest.PageAnnotation.builder()
                                    .page(pa.getPage())
                                    .startPos(pa.getStartPos())
                                    .endPos(pa.getEndPos())
                                    .preview(pa.getPreview())
                                    .build())
                            .toList() : null)
                    .source(document != null ? document.getFilePath() : null)
                    .uploadedBy(document != null ? document.getOwnerId() : null)
                    .uploadedAt(document != null && document.getCreatedAt() != null
                            ? java.sql.Timestamp.valueOf(document.getCreatedAt()) : null)
                    .parsedAt(new java.util.Date())
                    .build();

            esIndexService.indexDocument(request);
            log.debug("[ES] Document indexed: documentId={}", documentId);

        } catch (Exception e) {
            log.error("[ES] Failed to index document: documentId={}, error={}", documentId, e.getMessage());
            // 不抛出异常，Qdrant写入已成功，ES由补偿任务重试
        }
    }

    /**
     * 批量向量化多个文档
     */
    public int batchVectorize(List<Map<String, String>> documents) {
        int totalStored = 0;
        for (Map<String, String> doc : documents) {
            try {
                int stored = vectorizeMarkdown(
                        doc.get("text"),
                        doc.get("collectionId"),
                        doc.get("documentId"),
                        doc.get("title"),
                        doc.getOrDefault("strategy", "fixed"),
                        DocType.NORMAL,
                        null,
                        null
                );
                totalStored += stored;
            } catch (Exception e) {
                log.error("批量向量化失败: documentId={}, error={}",
                        doc.get("documentId"), e.getMessage());
            }
        }
        return totalStored;
    }

    /**
     * 删除文档在Qdrant中的所有向量
     */
    public long deleteDocumentVectors(String collectionId, String documentId) {
        try {
            // 从document_chunks表统计该文档的向量数量（避免Qdrant集合级计数的竞态问题）
            long chunkCount = documentChunkService.countByDocumentId(documentId);

            Map<String, Object> filter = Map.of("document_id", documentId);
            vectorCrudService.deleteVectorsByFilter(collectionId, filter);

            log.info("删除文档向量完成: collectionId={}, documentId={}, deletedCount={}", collectionId, documentId, chunkCount);
            return chunkCount;
        } catch (Exception e) {
            log.error("删除文档向量失败: collectionId={}, documentId={}, error={}",
                    collectionId, documentId, e.getMessage());
            throw new RuntimeException("删除文档向量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新文档内容（删除旧向量 + 重新向量化）
     * 用于OCR编辑后的内容更新
     *
     * @param markdownText 新的markdown文本内容
     * @param collectionId 知识库ID
     * @param documentId 文档ID
     * @param title 文档标题
     * @param strategy 分块策略
     * @return 重新存储的向量点数量
     */
    public int updateDocumentContent(String markdownText, String collectionId, String documentId, String title,
                                     String strategy) {
        log.info("更新文档内容: collectionId={}, documentId={}, title={}, strategy={}, textLength={}",
                collectionId, documentId, title, strategy, markdownText.length());

        // 0. 获取旧chunk数量并扣减
        Document document = documentMapper.selectById(documentId);
        int oldChunkCount = document != null ? document.getChunkCount() : 0;
        if (oldChunkCount > 0) {
            decrementCollectionChunkCount(collectionId, oldChunkCount);
            log.debug("扣减旧chunk计数: collectionId={}, oldChunkCount={}", collectionId, oldChunkCount);
        }

        // 1. 删除旧向量（Qdrant）
        try {
            deleteDocumentVectors(collectionId, documentId);
            log.debug("旧向量已删除: collectionId={}, documentId={}", collectionId, documentId);
        } catch (Exception e) {
            log.error("删除旧向量失败: collectionId={}, documentId={}, error={}",
                    collectionId, documentId, e.getMessage());
            // 即使删除失败，也继续重新向量化，旧计数已扣减
        }

        // 2. 删除旧ES索引
        try {
            esIndexService.deleteDocument(collectionId, documentId);
            log.debug("旧ES索引已删除: collectionId={}, documentId={}", collectionId, documentId);
        } catch (Exception e) {
            log.warn("删除旧ES索引失败（不影响）: collectionId={}, documentId={}, error={}",
                    collectionId, documentId, e.getMessage());
        }

        // 3. 重新向量化存储（会自动增加chunkCount），保持原始DocType
        DocType docType = document != null ? document.getDocType() : DocType.NORMAL;
        int storedCount = vectorizeMarkdown(markdownText, collectionId, documentId, title, strategy, docType, null, null);

        log.info("文档内容更新完成: collectionId={}, documentId={}, oldChunkCount={}, newChunkCount={}",
                collectionId, documentId, oldChunkCount, storedCount);
        return storedCount;
    }

    /**
     * 确保Qdrant collection存在
     */
    private void ensureCollectionExists(String collectionId) {
        try {
            collectionManagementService.getCollectionInfo(collectionId);
            log.debug("Qdrant collection已存在: collectionId={}", collectionId);
        } catch (Exception e) {
            log.info("Qdrant collection不存在或无法访问，创建: collectionId={}, reason={}",
                    collectionId, e.getMessage());
            collectionManagementService.createCollectionWithDefaultConfig(collectionId);
        }
    }

    /**
     * 更新Collection的chunk计数（增加）
     */
    private synchronized void updateCollectionChunkCount(String collectionId, int increment) {
        try {
            Collection collection = collectionMapper.selectById(collectionId);
            if (collection != null) {
                collection.incrementChunkCount(increment);
                collectionMapper.updateById(collection);
                log.debug("更新Collection chunk计数: collectionId={}, increment={}", collectionId, increment);
            }
        } catch (Exception e) {
            log.warn("更新Collection chunk计数失败: collectionId={}: {}", collectionId, e);
        }
    }

    /**
     * 更新Collection的chunk计数（扣减）
     */
    private synchronized void decrementCollectionChunkCount(String collectionId, int decrement) {
        try {
            Collection collection = collectionMapper.selectById(collectionId);
            if (collection != null) {
                collection.decrementChunkCount(decrement);
                collectionMapper.updateById(collection);
                log.debug("扣减Collection chunk计数: collectionId={}, decrement={}", collectionId, decrement);
            }
        } catch (Exception e) {
            log.warn("扣减Collection chunk计数失败: collectionId={}: {}", collectionId, e);
        }
    }

    /**
     * 构建包含权限元数据的payload（使用预查询的Document和Permission）
     */
    private Map<String, Object> buildPayloadWithPermission(String documentId, String title, TextChunker.Chunk chunk,
                                                            Document document, DocumentPermission permission,
                                                            Map<String, Object> keySections) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("document_id", documentId);
        payload.put("title", title);
        payload.put("text", chunk.text());
        payload.put("chunk_index", chunk.index());
        payload.put("start_pos", chunk.startPos());
        payload.put("end_pos", chunk.endPos());
        payload.put("start_page", chunk.startPage());   // 页码信息
        payload.put("end_page", chunk.endPage());
        payload.put("content_length", chunk.text().length());
        payload.put("doc_type", DocType.NORMAL.name());

        // 语义分块摘要（仅semantic策略有效，辅助检索匹配）
        if (chunk.summary() != null && !chunk.summary().isBlank()) {
            payload.put("summary", chunk.summary());
        }

        // NER实体注入payload（用于Qdrant payload filter）
        if (keySections != null && !keySections.isEmpty()) {
            extractListField(payload, keySections, "_drug_names", "drug_names");
            extractListField(payload, keySections, "_manufacturers", "manufacturers");
            extractListField(payload, keySections, "_regions", "regions");
        }

        injectPermissionMetadata(payload, document, permission);

        return payload;
    }

    /**
     * 从keySections中安全提取字符串字段到payload顶层
     */
    private void extractStringField(Map<String, Object> payload, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof String s && !s.isBlank()) {
            payload.put(key, s);
        }
    }

    /**
     * 从keySections中安全提取列表字段到payload顶层
     * @param sourceKey keySections中的key（带_前缀，如 "_diseases"）
     * @param targetKey payload中的目标key（如 "diseases"）
     */
    @SuppressWarnings("unchecked")
    private void extractListField(Map<String, Object> payload, Map<String, Object> source,
                                  String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value instanceof List<?> list && !list.isEmpty()) {
            // 过滤出字符串元素
            List<String> strings = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    strings.add(s);
                }
            }
            if (!strings.isEmpty()) {
                payload.put(targetKey, strings);
            }
        }
    }
}