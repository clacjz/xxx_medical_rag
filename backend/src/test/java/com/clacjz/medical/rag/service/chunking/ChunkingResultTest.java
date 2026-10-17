package com.clacjz.medical.rag.service.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkingResult 单元测试")
class ChunkingResultTest {

    @Nested
    @DisplayName("ChunkingResult - 分块结果")
    class ChunkingResultTests {

        @Test
        @DisplayName("使用builder创建完整结果")
        void shouldBuildCompleteResult() {
            LocalDateTime now = LocalDateTime.now();
            ChunkingResult.ChunkConfig config = new ChunkingResult.ChunkConfig(500, 50);
            List<ChunkingResult.ChunkInfo> chunks = List.of(
                    new ChunkingResult.ChunkInfo(0, "第一块内容", 0, 100, 1, 1, ""),
                    new ChunkingResult.ChunkInfo(1, "第二块内容", 100, 200, 2, 2, "")
            );

            ChunkingResult result = ChunkingResult.builder()
                    .documentId("doc-123")
                    .totalChars(200)
                    .strategy("fixed")
                    .config(config)
                    .chunkCount(2)
                    .chunks(chunks)
                    .previewAt(now)
                    .build();

            assertEquals("doc-123", result.getDocumentId());
            assertEquals(200, result.getTotalChars());
            assertEquals("fixed", result.getStrategy());
            assertEquals(2, result.getChunkCount());
            assertEquals(2, result.getChunks().size());
            assertEquals(now, result.getPreviewAt());
        }

        @Test
        @DisplayName("访问属性方法")
        void shouldAccessProperties() {
            ChunkingResult.ChunkConfig config = new ChunkingResult.ChunkConfig(500, 50);
            ChunkingResult result = ChunkingResult.builder()
                    .documentId("doc-123")
                    .totalChars(200)
                    .strategy("fixed")
                    .config(config)
                    .chunkCount(2)
                    .build();

            assertEquals(2, result.chunkCount());
            assertEquals(200, result.totalChars());
            assertEquals("fixed", result.strategy());
            assertEquals(config, result.config());
        }

        @Test
        @DisplayName("空chunks列表")
        void shouldHandleEmptyChunks() {
            ChunkingResult result = ChunkingResult.builder()
                    .documentId("doc-123")
                    .totalChars(0)
                    .strategy("fixed")
                    .chunkCount(0)
                    .chunks(List.of())
                    .build();

            assertEquals(0, result.getChunkCount());
            assertTrue(result.getChunks().isEmpty());
        }
    }

    @Nested
    @DisplayName("ChunkConfig - 分块配置")
    class ChunkConfigTests {

        @Test
        @DisplayName("创建配置对象")
        void shouldCreateConfig() {
            ChunkingResult.ChunkConfig config = new ChunkingResult.ChunkConfig(500, 50);
            assertEquals(500, config.getChunkSize());
            assertEquals(50, config.getOverlap());
        }

        @Test
        @DisplayName("访问配置属性方法")
        void shouldAccessConfigProperties() {
            ChunkingResult.ChunkConfig config = new ChunkingResult.ChunkConfig(500, 50);
            assertEquals(500, config.chunkSize());
            assertEquals(50, config.overlap());
        }

        @Test
        @DisplayName("零值配置")
        void shouldHandleZeroValues() {
            ChunkingResult.ChunkConfig config = new ChunkingResult.ChunkConfig(0, 0);
            assertEquals(0, config.getChunkSize());
            assertEquals(0, config.getOverlap());
        }
    }

    @Nested
    @DisplayName("ChunkInfo - 单块信息")
    class ChunkInfoTests {

        @Test
        @DisplayName("创建块信息")
        void shouldCreateChunkInfo() {
            ChunkingResult.ChunkInfo info = new ChunkingResult.ChunkInfo(
                    0, "测试文本", 0, 100, 1, 3, "摘要内容");

            assertEquals(0, info.getIndex());
            assertEquals("测试文本", info.getText());
            assertEquals(0, info.getStartPos());
            assertEquals(100, info.getEndPos());
            assertEquals(1, info.getStartPage());
            assertEquals(3, info.getEndPage());
            assertEquals("摘要内容", info.getSummary());
        }

        @Test
        @DisplayName("访问块信息属性方法")
        void shouldAccessChunkInfoProperties() {
            ChunkingResult.ChunkInfo info = new ChunkingResult.ChunkInfo(
                    1, "第二块", 100, 200, 2, 2, "");

            assertEquals(1, info.index());
            assertEquals("第二块", info.text());
            assertEquals(100, info.startPos());
            assertEquals(200, info.endPos());
            assertEquals(2, info.startPage());
            assertEquals(2, info.endPage());
            assertEquals("", info.summary());
        }

        @Test
        @DisplayName("无页码信息时startPage和endPage为0")
        void shouldHandleZeroPageNumbers() {
            ChunkingResult.ChunkInfo info = new ChunkingResult.ChunkInfo(
                    0, "内容", 0, 50, 0, 0, "");

            assertEquals(0, info.getStartPage());
            assertEquals(0, info.getEndPage());
        }

        @Test
        @DisplayName("语义分块摘要可为空")
        void shouldAllowEmptySummary() {
            ChunkingResult.ChunkInfo info = new ChunkingResult.ChunkInfo(
                    0, "内容", 0, 50, 0, 0, null);

            assertNull(info.getSummary());
        }
    }

    @Nested
    @DisplayName("多chunk场景")
    class MultipleChunksTests {

        @Test
        @DisplayName("多个chunks的完整信息")
        void shouldHandleMultipleChunks() {
            List<ChunkingResult.ChunkInfo> chunks = List.of(
                    new ChunkingResult.ChunkInfo(0, "第一页内容", 0, 100, 1, 1, ""),
                    new ChunkingResult.ChunkInfo(1, "第二页内容", 100, 200, 2, 2, ""),
                    new ChunkingResult.ChunkInfo(2, "第三页内容", 200, 300, 3, 3, "")
            );

            ChunkingResult result = ChunkingResult.builder()
                    .documentId("doc-123")
                    .totalChars(300)
                    .strategy("paragraph")
                    .chunkCount(3)
                    .chunks(chunks)
                    .build();

            assertEquals(3, result.getChunks().size());
            assertEquals("第一页内容", result.getChunks().get(0).getText());
            assertEquals("第二页内容", result.getChunks().get(1).getText());
            assertEquals("第三页内容", result.getChunks().get(2).getText());
        }

        @Test
        @DisplayName("跨页chunk的页码范围")
        void shouldHandleChunkSpanningPages() {
            ChunkingResult.ChunkInfo info = new ChunkingResult.ChunkInfo(
                    0, "跨页内容", 50, 150, 1, 2, "");

            assertEquals(1, info.getStartPage());
            assertEquals(2, info.getEndPage());
        }
    }
}