package com.clacjz.medical.rag.service.chunking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.service.TempFileCleanupService;
import com.clacjz.medical.rag.service.parser.PageLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChunkingService 单元测试")
class ChunkingServiceTest {

    @Mock
    private TextChunker textChunker;

    @Mock
    private SemanticChunkingService semanticChunkingService;

    @Mock
    private ParserProperties parserProperties;

    @Mock
    private ParserProperties.ChunkConfig chunkConfig;

    @Mock
    private TempFileCleanupService tempFileCleanupService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        when(parserProperties.getChunk()).thenReturn(chunkConfig);
        when(chunkConfig.getSize()).thenReturn(500);
        when(chunkConfig.getOverlap()).thenReturn(50);
        chunkingService = new ChunkingService(textChunker, semanticChunkingService, parserProperties, tempFileCleanupService, objectMapper);
    }

    @Nested
    @DisplayName("分块策略分发")
    class StrategyDispatchTests {

        @Test
        @DisplayName("semantic 策略调用 semanticChunkingService")
        void shouldUseSemanticForSemanticStrategy() {
            SemanticChunkingService.SemanticChunkResult semanticResult = mock(SemanticChunkingService.SemanticChunkResult.class);
            SemanticChunkingService.SemanticChunkResult.ChunkItem item = mock(SemanticChunkingService.SemanticChunkResult.ChunkItem.class);
            when(item.getOriginal()).thenReturn("text");
            when(item.getStartPos()).thenReturn(0);
            when(item.getEndPos()).thenReturn(4);
            when(item.getSummary()).thenReturn("sum");
            when(semanticResult.getChunks()).thenReturn(List.of(item));
            when(semanticChunkingService.semanticChunk(anyString(), any(Integer.class))).thenReturn(semanticResult);

            ChunkingResult result = chunkingService.chunk("doc-1", "some text", "semantic");

            assertEquals(1, result.getChunks().size());
            assertEquals("semantic", result.getStrategy());
            verify(semanticChunkingService).semanticChunk(anyString(), any(Integer.class));
        }

        @Test
        @DisplayName("semantic 策略大小写不敏感")
        void shouldBeCaseInsensitiveForSemantic() {
            SemanticChunkingService.SemanticChunkResult semanticResult = mock(SemanticChunkingService.SemanticChunkResult.class);
            when(semanticResult.getChunks()).thenReturn(List.of());
            when(semanticChunkingService.semanticChunk(anyString(), any(Integer.class))).thenReturn(semanticResult);

            ChunkingResult result = chunkingService.chunk("doc-1", "text", "Semantic");

            assertEquals("Semantic", result.getStrategy());
            verify(semanticChunkingService).semanticChunk(anyString(), any(Integer.class));
        }

        @Test
        @DisplayName("paragraph 策略无 layout 走 chunkByParagraph")
        void shouldUseParagraphWithoutLayout() {
            when(textChunker.chunkByParagraph(anyString())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "p1", 0, 2, 0, 0)
            ));

            ChunkingResult result = chunkingService.chunk("doc-1", "text", "paragraph", null);

            assertEquals(1, result.getChunks().size());
            verify(textChunker).chunkByParagraph(anyString());
            verify(textChunker, never()).chunkByParagraphWithLayout(anyString(), any());
        }

        @Test
        @DisplayName("paragraph 策略有 layout 走 chunkByParagraphWithLayout")
        void shouldUseParagraphWithLayout() {
            PageLayout layout = new PageLayout(1, "page1", 0, 5);
            when(textChunker.chunkByParagraphWithLayout(anyString(), any())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "p1", 0, 2, 1, 1)
            ));

            ChunkingResult result = chunkingService.chunk("doc-1", "text", "paragraph", List.of(layout));

            assertEquals(1, result.getChunks().size());
            verify(textChunker).chunkByParagraphWithLayout(anyString(), any());
        }

        @Test
        @DisplayName("paragraph 策略 layout 为空列表走 chunkByParagraph")
        void shouldUseParagraphWithEmptyLayout() {
            when(textChunker.chunkByParagraph(anyString())).thenReturn(List.of());

            chunkingService.chunk("doc-1", "text", "paragraph", List.of());

            verify(textChunker).chunkByParagraph(anyString());
        }

        @Test
        @DisplayName("character 策略走 chunkByDelimiter")
        void shouldUseDelimiterForCharacter() {
            when(textChunker.chunkByDelimiter(anyString(), eq("both"))).thenReturn(List.of());

            chunkingService.chunk("doc-1", "text", "character");

            verify(textChunker).chunkByDelimiter(anyString(), eq("both"));
        }

        @Test
        @DisplayName("sentence 策略走 chunkByDelimiter")
        void shouldUseDelimiterForSentence() {
            when(textChunker.chunkByDelimiter(anyString(), eq("both"))).thenReturn(List.of());

            chunkingService.chunk("doc-1", "text", "sentence");

            verify(textChunker).chunkByDelimiter(anyString(), eq("both"));
        }

        @Test
        @DisplayName("none 策略走 chunkNoSplit")
        void shouldUseNoSplitForNoneStrategy() {
            when(textChunker.chunkNoSplit(anyString())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "all", 0, 3, 0, 0)
            ));

            ChunkingResult result = chunkingService.chunk("doc-1", "all", "none");

            assertEquals(1, result.getChunks().size());
            assertEquals("all", result.getChunks().get(0).getText());
            verify(textChunker).chunkNoSplit(anyString());
        }

        @Test
        @DisplayName("未知策略走默认 chunkFixedSize")
        void shouldUseFixedSizeForUnknownStrategy() {
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "c0", 0, 2, 0, 0)
            ));

            ChunkingResult result = chunkingService.chunk("doc-1", "text", "unknown-strategy");

            assertEquals(1, result.getChunks().size());
            verify(textChunker).chunkFixedSize(anyString());
        }

        @Test
        @DisplayName("null strategy 走默认 chunkFixedSize")
        void shouldUseFixedSizeForNullStrategy() {
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());

            chunkingService.chunk("doc-1", "text", null);

            verify(textChunker).chunkFixedSize(anyString());
        }

        @Test
        @DisplayName("空 strategy 走默认 chunkFixedSize")
        void shouldUseFixedSizeForEmptyStrategy() {
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of());

            chunkingService.chunk("doc-1", "text", "");

            verify(textChunker).chunkFixedSize(anyString());
        }
    }

    @Nested
    @DisplayName("ChunkingResult 元数据")
    class ResultMetadataTests {

        @Test
        @DisplayName("记录 documentId、长度、strategy、chunkSize/overlap")
        void shouldPopulateResultMetadata() {
            when(textChunker.chunkFixedSize(anyString())).thenReturn(List.of(
                    new TextChunker.Chunk(0, "a", 0, 1, 0, 0, ""),
                    new TextChunker.Chunk(1, "b", 2, 3, 0, 0, "")
            ));

            ChunkingResult result = chunkingService.chunk("doc-abc", "abcdef", "fixed");

            assertEquals("doc-abc", result.getDocumentId());
            assertEquals(6, result.getTotalChars());
            assertEquals("fixed", result.getStrategy());
            assertNotNull(result.getConfig());
            assertEquals(500, result.getConfig().getChunkSize());
            assertEquals(50, result.getConfig().getOverlap());
            assertEquals(2, result.getChunkCount());
            assertNotNull(result.getChunks());
            assertNotNull(result.getPreviewAt());
        }
    }

    @Nested
    @DisplayName("保存/加载分块预览")
    class PreviewPersistenceTests {

        @Test
        @DisplayName("saveChunkPreview 写入到 tempDir")
        void shouldSaveChunkPreview() throws Exception {
            Path tempDir = Files.createTempDirectory("chunk-test-");
            when(tempFileCleanupService.ensureTempDirectory()).thenReturn(tempDir);

            ChunkingResult result = new ChunkingResult(
                    "doc-1", 10, "fixed",
                    new ChunkingResult.ChunkConfig(100, 20),
                    1,
                    List.of(new ChunkingResult.ChunkInfo(0, "abc", 0, 3, 0, 0, "")),
                    java.time.LocalDateTime.now()
            );

            String filePath = chunkingService.saveChunkPreview("doc-1", result);

            assertNotNull(filePath);
            File saved = new File(filePath);
            assertTrue(saved.exists());
            assertEquals("doc-1_chunks.json", saved.getName());
            saved.delete();
            Files.deleteIfExists(tempDir);
        }

        @Test
        @DisplayName("saveChunkPreview 失败抛 RuntimeException")
        void shouldThrowOnSaveFailure() throws Exception {
            when(tempFileCleanupService.ensureTempDirectory()).thenThrow(new RuntimeException("disk full"));

            ChunkingResult result = new ChunkingResult(
                    "doc-1", 0, "fixed",
                    new ChunkingResult.ChunkConfig(100, 20),
                    0, List.of(), java.time.LocalDateTime.now()
            );

            assertThrows(RuntimeException.class, () -> chunkingService.saveChunkPreview("doc-1", result));
        }

        @Test
        @DisplayName("loadChunkPreview 文件不存在抛异常")
        void shouldThrowWhenFileNotFound() {
            assertThrows(RuntimeException.class,
                    () -> chunkingService.loadChunkPreview("/non/existent/path/abc.json"));
        }

        @Test
        @DisplayName("loadChunkPreview 解析错误抛异常")
        void shouldThrowOnParseError() throws Exception {
            Path tempFile = Files.createTempFile("bad-chunk-", ".json");
            Files.writeString(tempFile, "{not valid json");

            assertThrows(RuntimeException.class, () -> chunkingService.loadChunkPreview(tempFile.toString()));

            Files.deleteIfExists(tempFile);
        }
    }
}
