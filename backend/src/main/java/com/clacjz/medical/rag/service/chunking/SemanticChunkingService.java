package com.clacjz.medical.rag.service.chunking;

import com.clacjz.medical.rag.service.LLMService;
import com.clacjz.medical.rag.service.parser.TextCleaner;
import com.clacjz.medical.rag.util.SimilarityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 语义分块服务
 * 使用TextTiling轻量分段 + LLM摘要生成实现语义分块
 *
 * 处理流程：
 * 1. 轻量分段（TextTiling + 句子边界检测）
 * 2. 对每个分段调用LLM生成摘要
 * 3. 根据summary进行切片整合 - 相邻分段如果summary语义相似则合并
 * 4. 返回最终chunks（包含original原文和summary摘要）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticChunkingService {

    private final TextCleaner textCleaner;
    private final LLMService llmService;

    private static final String SUMMARY_PROMPT_TEMPLATE =
            "请为以下文本段落生成一个简短的摘要（20字以内），概括其主要内容：\n\n%s";

    /**
     * 语义分块
     *
     * @param text 输入文本
     * @param maxSegments 最大分段数
     * @return 分段结果
     */
    public SemanticChunkResult semanticChunk(String text, int maxSegments) {
        log.info("[Step: semanticChunking] Starting, textLength={}, maxSegments={}",
                text != null ? text.length() : 0, maxSegments);

        if (text == null || text.isBlank()) {
            log.warn("[Step: semanticChunking] Empty text, returning empty result");
            return new SemanticChunkResult(List.of());
        }

        // 1. 轻量分段
        List<Segment> candidates = lightweightSegmentation(text, maxSegments);
        log.info("[Step: semanticChunking] Lightweight segmentation done, candidateCount={}", candidates.size());

        // 2. 对每个分段调用LLM生成摘要
        List<SegmentWithSummary> segmentsWithSummary = generateSummaries(candidates);
        log.info("[Step: semanticChunking] Summary generation done, count={}", segmentsWithSummary.size());

        // 3. 根据摘要进行切片整合（合并相邻的相似主题分段）
        List<SemanticChunkResult.ChunkItem> finalChunks = mergeSimilarSegments(segmentsWithSummary);
        log.info("[Step: semanticChunking] Merging done, finalChunkCount={}", finalChunks.size());

        return new SemanticChunkResult(finalChunks);
    }

    /**
     * 轻量分段 - 使用TextTiling算法
     */
    private List<Segment> lightweightSegmentation(String text, int maxSegments) {
        List<Segment> segments = new ArrayList<>();

        // 清理文本
        String cleanedText = textCleaner.clean(text);
        if (cleanedText.isBlank()) {
            return segments;
        }

        // 计算分段大小
        int windowSize = Math.max(200, cleanedText.length() / maxSegments);
        int step = windowSize / 2; // 50% overlap

        int startPos = 0;
        int index = 0;

        while (startPos < cleanedText.length() && index < maxSegments) {
            int endPos = Math.min(startPos + windowSize, cleanedText.length());

            // 在句子边界处截断
            if (endPos < cleanedText.length()) {
                int sentenceBreak = findSentenceBoundary(cleanedText, startPos, endPos);
                if (sentenceBreak > startPos) {
                    endPos = sentenceBreak + 1;
                }
            }

            String segmentText = cleanedText.substring(startPos, endPos).trim();
            if (!segmentText.isBlank()) {
                segments.add(new Segment(index, segmentText, startPos, endPos));
                index++;
            }

            if (endPos >= cleanedText.length()) {
                break;
            }

            startPos += step;
        }

        return segments;
    }

    /**
     * 在指定范围内查找句子边界
     */
    private int findSentenceBoundary(String text, int start, int end) {
        // 句子结束标记：。！？!?
        int lastPeriod = text.lastIndexOf('。', end - 1);
        int lastQuestion = text.lastIndexOf('？', end - 1);
        int lastExclaim = text.lastIndexOf('！', end - 1);
        int lastNewline = text.lastIndexOf('\n', end - 1);

        int boundary = Math.max(lastPeriod, Math.max(lastQuestion, lastExclaim));
        boundary = Math.max(boundary, lastNewline);

        // 如果找到的边界在start之后，返回它
        if (boundary > start + 50) { // 至少要有50个字符
            return boundary;
        }

        // 否则在单词边界处截断
        int lastSpace = text.lastIndexOf(' ', end - 1);
        if (lastSpace > start + 50) {
            return lastSpace;
        }

        return end - 1;
    }

    /**
     * 为每个分段生成LLM摘要（并行执行，每段超时10秒）
     * 避免 N 个分段串行调用 LLM 导致整个文档处理卡死
     */
    private List<SegmentWithSummary> generateSummaries(List<Segment> segments) {
        if (segments.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<SegmentWithSummary>> futures = new ArrayList<>();

        for (Segment segment : segments) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String prompt = String.format(SUMMARY_PROMPT_TEMPLATE, segment.text());
                    String summary = llmService.generate(
                            "你是一个专业的文本摘要助手。请为文本生成简短摘要。",
                            prompt
                    );
                    summary = summary.trim().replaceAll("\n", " ");
                    if (summary.length() > 50) {
                        summary = summary.substring(0, 50);
                    }
                    return new SegmentWithSummary(
                            segment.index(), segment.text(),
                            segment.startPos(), segment.endPos(), summary
                    );
                } catch (Exception e) {
                    log.warn("[Step: semanticChunking] Failed to generate summary for segment {}: {}",
                            segment.index(), e.getMessage());
                    String fallback = segment.text().substring(0, Math.min(20, segment.text().length()));
                    return new SegmentWithSummary(
                            segment.index(), segment.text(),
                            segment.startPos(), segment.endPos(), fallback
                    );
                }
            }));
        }

        List<SegmentWithSummary> result = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                // 每段最多等 10 秒，超时则使用 fallback
                result.add(futures.get(i).get(10, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                Segment seg = segments.get(i);
                log.warn("[Step: semanticChunking] Summary generation timed out for segment {}, using fallback", i);
                String fallback = seg.text().substring(0, Math.min(20, seg.text().length()));
                result.add(new SegmentWithSummary(seg.index(), seg.text(), seg.startPos(), seg.endPos(), fallback));
                futures.get(i).cancel(true);
            } catch (InterruptedException | ExecutionException e) {
                Segment seg = segments.get(i);
                log.warn("[Step: semanticChunking] Summary generation failed for segment {}: {}", i, e);
                String fallback = seg.text().substring(0, Math.min(20, seg.text().length()));
                result.add(new SegmentWithSummary(seg.index(), seg.text(), seg.startPos(), seg.endPos(), fallback));
            }
        }

        return result;
    }

    /**
     * 根据摘要合并相邻的相似主题分段
     * 合并策略：维护一个参考摘要（当前 chunk 第一段的摘要），后续段与之比较。
     * 避免拼接摘要导致后续比较失真。
     */
    private List<SemanticChunkResult.ChunkItem> mergeSimilarSegments(List<SegmentWithSummary> segments) {
        if (segments.isEmpty()) {
            return List.of();
        }

        List<SemanticChunkResult.ChunkItem> chunks = new ArrayList<>();
        StringBuilder currentOriginal = new StringBuilder();
        String referenceSummary = null; // chunk 第一段的摘要，作为合并比较基准
        int currentStartPos = -1;
        int currentEndPos = -1;

        for (SegmentWithSummary segment : segments) {
            if (currentStartPos == -1) {
                // 第一个分段
                currentOriginal.append(segment.text());
                referenceSummary = segment.summary();
                currentStartPos = segment.startPos();
                currentEndPos = segment.endPos();
            } else if (referenceSummary != null
                    && SimilarityUtil.isSimilar(referenceSummary, segment.summary(), SIMILARITY_THRESHOLD)) {
                // 主题相似，合并（保留参考摘要不变）
                currentOriginal.append("\n\n").append(segment.text());
                currentEndPos = segment.endPos();
            } else {
                // 主题不同，保存当前chunk，开始新chunk
                chunks.add(new SemanticChunkResult.ChunkItem(
                        currentOriginal.toString(),
                        referenceSummary != null ? referenceSummary : "",
                        currentStartPos,
                        currentEndPos
                ));

                currentOriginal = new StringBuilder(segment.text());
                referenceSummary = segment.summary();
                currentStartPos = segment.startPos();
                currentEndPos = segment.endPos();
            }
        }

        // 保存最后一个chunk
        if (currentOriginal.length() > 0) {
            chunks.add(new SemanticChunkResult.ChunkItem(
                    currentOriginal.toString(),
                    referenceSummary != null ? referenceSummary : "",
                    currentStartPos,
                    currentEndPos
            ));
        }

        return chunks;
    }

    /** 摘要合并相似度阈值：用字符级 Jaccard 比较，0.6 意味着两段摘要共享大部分字符即为同类主题 */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    // 内部类：分段
    private record Segment(int index, String text, int startPos, int endPos) {}

    // 内部类：带摘要的分段
    private record SegmentWithSummary(int index, String text, int startPos, int endPos, String summary) {}

    /**
     * 语义分块结果
     */
    @lombok.Data
    @lombok.Builder
    public static class SemanticChunkResult {
        private List<ChunkItem> chunks;

        @lombok.Data
        @lombok.Builder
        public static class ChunkItem {
            /**
             * 原文片段
             */
            private String original;

            /**
             * LLM生成的摘要
             */
            private String summary;

            /**
             * 在原文中的起始位置
             */
            private int startPos;

            /**
             * 在原文中的结束位置
             */
            private int endPos;
        }
    }
}