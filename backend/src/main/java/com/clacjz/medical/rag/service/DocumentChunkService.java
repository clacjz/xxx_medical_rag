package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.mapper.DocumentChunkMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.DocumentChunk;
import com.clacjz.medical.rag.service.chunking.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 文档分块管理服务
 * 负责管理 document_chunks 表，实现与 Qdrant/ES 向量存储的元数据同步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkService {

    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存文档分块到数据库（与Qdrant存储同步）
     */
    @Transactional
    public void saveChunks(String documentId, List<TextChunker.Chunk> chunks, List<String> pointIds) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("保存分块失败: chunks为空, documentId={}", documentId);
            return;
        }

        // 先删除旧的分块记录
        documentChunkMapper.deleteByDocumentId(documentId);

        // 构建所有entity
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.Chunk chunk = chunks.get(i);
            String pointId = pointIds != null && i < pointIds.size() ? pointIds.get(i) : null;

            entities.add(DocumentChunk.builder()
                    .documentId(documentId)
                    .pointId(pointId)
                    .chunkIndex(chunk.index())
                    .content(chunk.text())
                    .chunkSize(chunk.text().length())
                    .startPos(chunk.startPos())
                    .endPos(chunk.endPos())
                    .startPage(chunk.startPage())
                    .endPage(chunk.endPage())
                    .metadata(toMetadataJson(chunk.index()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // 批量插入（每批200条，避免PostgreSQL参数限制65535）
        int batchSize = 200;
        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<DocumentChunk> batch = entities.subList(i, end);
            documentChunkMapper.batchInsertWithJsonb(batch);
        }

        log.info("保存文档分块完成: documentId={}, chunkCount={}", documentId, chunks.size());
    }

    private String toMetadataJson(int chunkIndex) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "created_at", LocalDateTime.now().toString(),
                    "source", "vectorization",
                    "chunk_index", chunkIndex
            ));
        } catch (JsonProcessingException e) {
            log.warn("序列化metadata失败: chunkIndex={}", chunkIndex, e);
            return "{}";
        }
    }

    /**
     * 获取文档的所有分块
     */
    public List<DocumentChunk> getChunksByDocumentId(String documentId) {
        return documentChunkMapper.selectByDocumentId(documentId);
    }

    /**
     * 根据pointId获取分块
     */
    public DocumentChunk getChunkByPointId(String pointId) {
        return documentChunkMapper.selectByPointId(pointId);
    }

    /**
     * 批量获取多个文档的 chunks，用于 HybridSearchService 处理 ES-only 结果
     * @param documentIds 文档ID列表
     * @return Map：documentId -> 该文档的 chunks 列表（按 chunk_index 排序）
     */
    public Map<String, List<DocumentChunk>> getChunksByDocumentIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DocumentChunk> allChunks = documentChunkMapper.selectByDocumentIds(documentIds);
        return allChunks.stream()
                .collect(Collectors.groupingBy(
                        DocumentChunk::getDocumentId,
                        TreeMap::new,
                        Collectors.toList()));
    }

    /**
     * 删除文档的所有分块
     */
    @Transactional
    public void deleteChunksByDocumentId(String documentId) {
        documentChunkMapper.deleteByDocumentId(documentId);
        log.info("删除文档分块: documentId={}", documentId);
    }

    /**
     * 获取文档分块数量
     */
    public long getChunkCount(String documentId) {
        return documentChunkMapper.selectByDocumentId(documentId).size();
    }

    /**
     * 统计文档分块数量（高效，使用COUNT查询）
     */
    public long countByDocumentId(String documentId) {
        return documentChunkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DocumentChunk>()
                        .eq("document_id", documentId));
    }
}