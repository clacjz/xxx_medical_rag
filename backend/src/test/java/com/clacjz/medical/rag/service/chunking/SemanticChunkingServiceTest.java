package com.clacjz.medical.rag.service.chunking;

import com.clacjz.medical.rag.service.LLMService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SemanticChunkingService 单元测试")
class SemanticChunkingServiceTest {

    @Mock
    private TextCleaner textCleaner;

    @Mock
    private LLMService llmService;

    private SemanticChunkingService semanticChunkingService;

    @BeforeEach
    void setUp() {
        semanticChunkingService = new SemanticChunkingService(textCleaner, llmService);
    }

    @Nested
    @DisplayName("semanticChunk - 语义分块")
    class SemanticChunkTests {

        @Test
        @DisplayName("null文本返回空结果")
        void shouldReturnEmptyWhenNullText() {
            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(null, 5);
            assertNotNull(result);
            assertTrue(result.getChunks().isEmpty());
        }

        @Test
        @DisplayName("空文本返回空结果")
        void shouldReturnEmptyWhenBlankText() {
            when(textCleaner.clean(anyString())).thenReturn("   ");
            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk("   ", 5);
            assertNotNull(result);
            assertTrue(result.getChunks().isEmpty());
        }

        @Test
        @DisplayName("短文本正常处理")
        void shouldProcessShortText() {
            String text = "这是一个测试文本。";
            when(textCleaner.clean(anyString())).thenReturn(text);
            when(llmService.generate(anyString(), anyString())).thenReturn("测试摘要");

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 3);

            assertNotNull(result);
            assertFalse(result.getChunks().isEmpty());
        }

        @Test
        @DisplayName("maxSegments控制分段数")
        void shouldRespectMaxSegments() {
            String text = "A".repeat(1000);
            when(textCleaner.clean(anyString())).thenReturn(text);
            // 使用不同的摘要以避免相似合并
            when(llmService.generate(anyString(), anyString()))
                    .thenReturn("摘要一")
                    .thenReturn("摘要二")
                    .thenReturn("摘要三");

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 3);

            // 3个分段
            assertEquals(3, result.getChunks().size());
        }

        @Test
        @DisplayName("LLM生成摘要失败时使用fallback")
        void shouldUseFallbackOnLLMFailure() {
            String text = "测试文本内容。";
            when(textCleaner.clean(anyString())).thenReturn(text);
            when(llmService.generate(anyString(), anyString()))
                    .thenThrow(new RuntimeException("LLM error"));

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 2);

            assertNotNull(result);
            assertFalse(result.getChunks().isEmpty());
        }

        @Test
        @DisplayName("返回结果包含chunk内容")
        void shouldReturnChunkWithContent() {
            String text = "测试文本。";
            when(textCleaner.clean(anyString())).thenReturn(text);
            when(llmService.generate(anyString(), anyString())).thenReturn("摘要内容");

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 2);

            assertFalse(result.getChunks().isEmpty());
            SemanticChunkingService.SemanticChunkResult.ChunkItem firstChunk =
                    result.getChunks().get(0);
            assertNotNull(firstChunk.getOriginal());
            assertNotNull(firstChunk.getSummary());
        }

        @Test
        @DisplayName("摘要过长时截断到50字符")
        void shouldTruncateLongSummary() {
            String text = "测试文本内容。";
            when(textCleaner.clean(anyString())).thenReturn(text);
            when(llmService.generate(anyString(), anyString()))
                    .thenReturn("A".repeat(100));

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 2);

            assertFalse(result.getChunks().isEmpty());
            assertTrue(result.getChunks().get(0).getSummary().length() <= 50);
        }

        @Test
        @DisplayName("chunk记录位置信息")
        void shouldRecordPositionInfo() {
            String text = "测试文本内容。";
            when(textCleaner.clean(anyString())).thenReturn(text);
            when(llmService.generate(anyString(), anyString())).thenReturn("摘要");

            SemanticChunkingService.SemanticChunkResult result =
                    semanticChunkingService.semanticChunk(text, 2);

            assertFalse(result.getChunks().isEmpty());
            SemanticChunkingService.SemanticChunkResult.ChunkItem chunk =
                    result.getChunks().get(0);
            assertTrue(chunk.getStartPos() >= 0);
            assertTrue(chunk.getEndPos() >= chunk.getStartPos());
        }
    }

    @Nested
    @DisplayName("SemanticChunkResult - 分块结果结构")
    class SemanticChunkResultTests {

        @Test
        @DisplayName("可以构建包含多个chunk的结果")
        void shouldBuildResultWithMultipleChunks() {
            SemanticChunkingService.SemanticChunkResult result =
                    SemanticChunkingService.SemanticChunkResult.builder()
                            .chunks(List.of(
                                    SemanticChunkingService.SemanticChunkResult.ChunkItem.builder()
                                            .original("第一段内容")
                                            .summary("第一段摘要")
                                            .startPos(0)
                                            .endPos(5)
                                            .build(),
                                    SemanticChunkingService.SemanticChunkResult.ChunkItem.builder()
                                            .original("第二段内容")
                                            .summary("第二段摘要")
                                            .startPos(5)
                                            .endPos(10)
                                            .build()
                            ))
                            .build();

            assertEquals(2, result.getChunks().size());
            assertEquals("第一段内容", result.getChunks().get(0).getOriginal());
            assertEquals("第二段内容", result.getChunks().get(1).getOriginal());
        }

        @Test
        @DisplayName("ChunkItem字段完整")
        void shouldHaveAllFields() {
            SemanticChunkingService.SemanticChunkResult.ChunkItem item =
                    SemanticChunkingService.SemanticChunkResult.ChunkItem.builder()
                            .original("原文")
                            .summary("摘要")
                            .startPos(0)
                            .endPos(10)
                            .build();

            assertEquals("原文", item.getOriginal());
            assertEquals("摘要", item.getSummary());
            assertEquals(0, item.getStartPos());
            assertEquals(10, item.getEndPos());
        }
    }
}