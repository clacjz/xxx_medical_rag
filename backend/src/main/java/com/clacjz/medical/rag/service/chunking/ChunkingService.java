package com.clacjz.medical.rag.service.chunking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.service.parser.PageLayout;
import com.clacjz.medical.rag.service.TempFileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final TextChunker textChunker;
    private final SemanticChunkingService semanticChunkingService;
    private final ParserProperties parserProperties;
    private final TempFileCleanupService tempFileCleanupService;
    private final ObjectMapper objectMapper;

    public ChunkingResult chunk(String documentId, String text, String strategy) {
        // 调用新的带 PageLayout 的方法
        return chunk(documentId, text, strategy, null);
    }

    /**
     * 带页面布局信息的分块
     * @param documentId 文档ID
     * @param text 文本内容
     * @param strategy 分块策略
     * @param pageLayouts 页面布局信息（可为 null）
     */
    public ChunkingResult chunk(String documentId, String text, String strategy, List<PageLayout> pageLayouts) {
        log.info("[Step: chunking] Starting chunk operation, documentId={}, strategy={}, textLength={}, hasPageLayouts={}",
                documentId, strategy, text.length(), pageLayouts != null && !pageLayouts.isEmpty());

        List<TextChunker.Chunk> chunks;

        if ("semantic".equalsIgnoreCase(strategy)) {
            // 语义分块
            SemanticChunkingService.SemanticChunkResult semanticResult =
                    semanticChunkingService.semanticChunk(text, 10);
            List<TextChunker.Chunk> semanticChunks = new ArrayList<>();
            int[] idx = {0};
            semanticResult.getChunks().stream()
                    .map(item -> new TextChunker.Chunk(
                            idx[0]++,
                            item.getOriginal(),
                            item.getStartPos(),
                            item.getEndPos(),
                            0, 0,  // 语义分块暂无页码信息
                            item.getSummary()))
                    .forEach(semanticChunks::add);
            chunks = semanticChunks;
            log.info("[Step: chunking] Semantic chunking generated {} chunks, documentId={}",
                    chunks.size(), documentId);
        } else if ("paragraph".equalsIgnoreCase(strategy)) {
            // 段落分块：支持页码布局
            chunks = pageLayouts != null && !pageLayouts.isEmpty()
                    ? textChunker.chunkByParagraphWithLayout(text, pageLayouts)
                    : textChunker.chunkByParagraph(text);
        } else if ("character".equalsIgnoreCase(strategy) || "sentence".equalsIgnoreCase(strategy)) {
            // 按符号分块（换行符或句子结束符）
            chunks = textChunker.chunkByDelimiter(text, "both");
            log.info("[Step: chunking] Character/sentence chunking generated {} chunks, documentId={}",
                    chunks.size(), documentId);
        } else if ("none".equalsIgnoreCase(strategy)) {
            // 不分块
            chunks = textChunker.chunkNoSplit(text);
            log.info("[Step: chunking] No chunking applied, documentId={}", documentId);
        } else {
            // 默认使用fixed size
            chunks = textChunker.chunkFixedSize(text);
        }

        log.info("[Step: chunking] Generated {} chunks, documentId={}", chunks.size(), documentId);

        ChunkingResult result = new ChunkingResult(
                documentId,
                text.length(),
                strategy,
                new ChunkingResult.ChunkConfig(
                        parserProperties.getChunk().getSize(),
                        parserProperties.getChunk().getOverlap()
                ),
                chunks.size(),
                chunks.stream()
                        .map(c -> new ChunkingResult.ChunkInfo(
                                c.index(),
                                c.text(),
                                c.startPos(),
                                c.endPos(),
                                c.startPage(),
                                c.endPage(),
                                c.summary()
                        ))
                        .toList(),
                LocalDateTime.now()
        );

        return result;
    }

    public String saveChunkPreview(String documentId, ChunkingResult result) {
        try {
            Path tempDir = tempFileCleanupService.ensureTempDirectory();
            String fileName = documentId + "_chunks.json";
            Path filePath = tempDir.resolve(fileName);

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(filePath.toFile(), result);

            log.debug("Saved chunk preview to: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("Failed to save chunk preview for document: {}", documentId, e);
            throw new RuntimeException("Failed to save chunk preview: " + e.getMessage(), e);
        }
    }

    public ChunkingResult loadChunkPreview(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new RuntimeException("Chunk preview file not found: " + filePath);
            }
            // 保存时写入的是 ChunkingResult 类型，读取也应当使用同一类型
            ChunkingResult result =
                    objectMapper.readValue(path.toFile(), ChunkingResult.class);
            return result;
        } catch (IOException e) {
            log.error("Failed to load chunk preview from: {}", filePath, e);
            throw new RuntimeException("Failed to load chunk preview: " + e.getMessage(), e);
        }
    }
}
