package com.clacjz.medical.rag.service.chunking;

import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.service.parser.TextCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TextChunker 单元测试")
class TextChunkerTest {

    @Mock
    private ParserProperties parserProperties;

    @Mock
    private ParserProperties.ChunkConfig chunkProperties;

    @Mock
    private TextCleaner textCleaner;

    private TextChunker textChunker;

    @BeforeEach
    void setUp() {
        when(parserProperties.getChunk()).thenReturn(chunkProperties);
        when(chunkProperties.getSize()).thenReturn(100);
        when(chunkProperties.getOverlap()).thenReturn(20);
        when(textCleaner.clean(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        textChunker = new TextChunker(parserProperties, textCleaner);
    }

    @Nested
    @DisplayName("chunkFixedSize - 固定大小分块")
    class ChunkFixedSizeTests {

        @Test
        @DisplayName("null文本返回空列表")
        void shouldReturnEmptyWhenNullText() {
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空文本返回空列表")
        void shouldReturnEmptyWhenBlankText() {
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("文本长度小于chunkSize返回单个chunk")
        void shouldReturnSingleChunkWhenTextShorterThanChunkSize() {
            String text = "这是短文本";
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text);
            assertEquals(1, result.size());
            assertEquals(text, result.get(0).text());
            assertEquals(0, result.get(0).startPos());
            assertEquals(text.length(), result.get(0).endPos());
        }

        @Test
        @DisplayName("文本长度等于chunkSize返回单个chunk")
        void shouldReturnSingleChunkWhenTextEqualsChunkSize() {
            // chunkSize = 100
            String text = "A".repeat(100);
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("长文本正确分块")
        void shouldChunkLongTextCorrectly() {
            // chunkSize=100, overlap=20, step=80
            String text = "A".repeat(200);
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text);
            // 200字符, step=80: [0-100), [80-180), [160-200)
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("使用自定义chunkSize和overlap")
        void shouldUseCustomChunkSizeAndOverlap() {
            String text = "A".repeat(150);
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text, 50, 10);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("分块时优先在句子边界断开")
        void shouldBreakAtSentenceBoundary() {
            // 文本包含句子边界 "。"
            String text = "第一句。第二句。第三句。" + "D".repeat(100);
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("chunkIndex从0开始递增")
        void shouldHaveIncrementingIndex() {
            String text = "A".repeat(300);
            List<TextChunker.Chunk> result = textChunker.chunkFixedSize(text);
            for (int i = 0; i < result.size(); i++) {
                assertEquals(i, result.get(i).index());
            }
        }
    }

    @Nested
    @DisplayName("chunkByParagraph - 按段落分块")
    class ChunkByParagraphTests {

        @Test
        @DisplayName("null文本返回空列表")
        void shouldReturnEmptyWhenNullText() {
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空文本返回空列表")
        void shouldReturnEmptyWhenBlankText() {
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单段落文本返回单个chunk")
        void shouldReturnSingleChunkForSingleParagraph() {
            when(textCleaner.splitIntoParagraphs(anyString())).thenReturn(
                    List.of("这是一个段落的内容。"));
            String text = "这是一个段落的内容。";
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph(text);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("多段落文本正确分块")
        void shouldChunkMultipleParagraphs() {
            when(textCleaner.splitIntoParagraphs(anyString())).thenReturn(
                    List.of("第一段", "第二段", "第三段"));
            String text = "第一段\n\n第二段\n\n第三段";
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph(text);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("使用自定义maxChunkSize")
        void shouldUseCustomMaxChunkSize() {
            when(textCleaner.splitIntoParagraphs(anyString())).thenReturn(
                    List.of("段落内容"));
            String text = "段落内容";
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph(text, 50);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("超长段落内部再分块")
        void shouldSplitOversizedParagraphInternally() {
            when(textCleaner.splitIntoParagraphs(anyString())).thenReturn(
                    List.of("A".repeat(200)));
            String text = "A".repeat(200);
            List<TextChunker.Chunk> result = textChunker.chunkByParagraph(text, 100);
            assertTrue(result.size() > 1);
        }
    }

    @Nested
    @DisplayName("chunkNoSplit - 不分块")
    class ChunkNoSplitTests {

        @Test
        @DisplayName("null文本返回空列表")
        void shouldReturnEmptyWhenNullText() {
            List<TextChunker.Chunk> result = textChunker.chunkNoSplit(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空文本返回空列表")
        void shouldReturnEmptyWhenBlankText() {
            List<TextChunker.Chunk> result = textChunker.chunkNoSplit("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("整篇文档作为单个chunk返回")
        void shouldReturnSingleChunkForEntireDocument() {
            String text = "这是一篇完整的文档内容。";
            List<TextChunker.Chunk> result = textChunker.chunkNoSplit(text);
            assertEquals(1, result.size());
            assertEquals(text, result.get(0).text());
            assertEquals(0, result.get(0).startPos());
            assertEquals(text.length(), result.get(0).endPos());
        }

        @Test
        @DisplayName("长文档也作为单个chunk返回")
        void shouldReturnSingleChunkForLongDocument() {
            String text = "A".repeat(500);
            List<TextChunker.Chunk> result = textChunker.chunkNoSplit(text);
            assertEquals(1, result.size());
            assertEquals(500, result.get(0).text().length());
        }
    }

    @Nested
    @DisplayName("chunkByDelimiter - 按分隔符分块")
    class ChunkByDelimiterTests {

        @Test
        @DisplayName("null文本返回空列表")
        void shouldReturnEmptyWhenNullText() {
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(null, "newline");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空文本返回空列表")
        void shouldReturnEmptyWhenBlankText() {
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter("   ", "newline");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("按换行符分块")
        void shouldSplitByNewline() {
            String text = "第一行\n第二行\n第三行";
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(text, "newline");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("按句子分块")
        void shouldSplitBySentence() {
            String text = "第一句。第二句。第三句。";
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(text, "sentence");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("按换行和句子分块")
        void shouldSplitByBoth() {
            String text = "第一句。\n第二句。";
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(text, "both");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("未知分隔符默认按换行分块")
        void shouldDefaultToNewlineForUnknownDelimiter() {
            String text = "第一行\n第二行";
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(text, "unknown");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("分隔后合并到chunkSize内")
        void shouldMergeSmallPartsWithinChunkSize() {
            String text = "短文本";
            List<TextChunker.Chunk> result = textChunker.chunkByDelimiter(text, "newline");
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Chunk record - 分块记录结构")
    class ChunkRecordTests {

        @Test
        @DisplayName("Chunk包含所有必要字段")
        void shouldContainAllFields() {
            TextChunker.Chunk chunk = new TextChunker.Chunk(
                    1, "测试文本", 10, 20, 1, 5, "摘要");
            assertEquals(1, chunk.index());
            assertEquals("测试文本", chunk.text());
            assertEquals(10, chunk.startPos());
            assertEquals(20, chunk.endPos());
            assertEquals(1, chunk.startPage());
            assertEquals(5, chunk.endPage());
            assertEquals("摘要", chunk.summary());
        }

        @Test
        @DisplayName("六参数构造函数正常工作")
        void shouldWorkWithSixParamConstructor() {
            TextChunker.Chunk chunk = new TextChunker.Chunk(
                    1, "测试文本", 10, 20, 1, 5);
            assertEquals("", chunk.summary());
        }
    }
}