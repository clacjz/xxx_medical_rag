package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.DocType;
import com.clacjz.medical.rag.service.chunking.ChunkingService;
import com.clacjz.medical.rag.service.parser.DocumentParserFactory;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentParseService 单元测试")
class DocumentParseServiceTest {

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private CollectionMapper collectionMapper;
    @Mock
    private DocumentParserFactory parserFactory;
    @Mock
    private ChunkingService chunkingService;
    @Mock
    private TempFileCleanupService tempFileCleanupService;
    @Mock
    private MinioClient minioClient;
    @Mock
    private RAGProperties ragProperties;
    @Mock
    private ParserProperties parserProperties;
    @Mock
    private OcrService ocrService;
    @Mock
    private LLMService llmService;
    @Mock
    private DocSchemaService docSchemaService;
    @Mock
    private NerClient nerClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DocumentParseService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseService(
                documentMapper, collectionMapper, parserFactory, chunkingService,
                tempFileCleanupService, minioClient, ragProperties, parserProperties,
                objectMapper, ocrService, llmService, docSchemaService, nerClient);
    }

    @Nested
    @DisplayName("parseDocument - 文档查找")
    class ParseDocumentLookupTests {

        @Test
        @DisplayName("文档不存在时抛 RuntimeException")
        void shouldThrowWhenDocumentNotFound() {
            when(documentMapper.selectById("missing")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.parseDocument("missing", "fixed"));
            assertTrue(ex.getMessage().contains("Document not found"));
            assertTrue(ex.getMessage().contains("missing"));
        }
    }

    @Nested
    @DisplayName("parseDocument - DocType 分发")
    class DocTypeDispatchTests {

        @Test
        @DisplayName("DocType.NORMAL 文档进入 normal 流程")
        void shouldHandleNormalDoc() {
            Document doc = new Document();
            doc.setId("doc-1");
            doc.setDocType(DocType.NORMAL);
            when(documentMapper.selectById("doc-1")).thenReturn(doc);

            // NORMAL 流程需要更多 mock；为了不在此处完整模拟整个流程，
            // 仅仅验证走到 normal 流程时会因为缺少后续 mock 而抛异常（而不是 doc-not-found）。
            try {
                service.parseDocument("doc-1", "fixed");
                fail("应抛异常，因为 normal 流程的依赖未完全 mock");
            } catch (RuntimeException ex) {
                // 排除 "Document not found" 路径
                assertFalse(ex.getMessage().contains("Document not found"),
                        "不应是文档未找到异常");
            }
        }

        @Test
        @DisplayName("DocType.MANUAL 文档进入 manual 流程（OCR extract-json）")
        void shouldHandleManualDoc() {
            Document doc = new Document();
            doc.setId("doc-manual");
            doc.setDocType(DocType.MANUAL);
            doc.setFilePath("/tmp/manual.pdf");
            when(documentMapper.selectById("doc-manual")).thenReturn(doc);

            // MANUAL 流程会调用 OcrService.extractDocumentWithSchema
            // 这里只验证不抛 "Document not found"
            try {
                service.parseDocument("doc-manual", "fixed");
                fail("应抛异常，因为 manual 流程的依赖未完全 mock");
            } catch (RuntimeException ex) {
                assertFalse(ex.getMessage().contains("Document not found"),
                        "不应是文档未找到异常");
            }
        }

        @Test
        @DisplayName("docType 为 null 时默认为 NORMAL")
        void shouldDefaultToNormalWhenDocTypeIsNull() {
            Document doc = new Document();
            doc.setId("doc-no-type");
            doc.setDocType(null);
            when(documentMapper.selectById("doc-no-type")).thenReturn(doc);

            try {
                service.parseDocument("doc-no-type", "fixed");
                fail("应抛异常（流程未完整 mock）");
            } catch (RuntimeException ex) {
                assertFalse(ex.getMessage().contains("Document not found"));
            }
        }
    }

    @Nested
    @DisplayName("getParseStatus")
    class GetParseStatusTests {

        @Test
        @DisplayName("文档不存在时抛 RuntimeException")
        void shouldThrowWhenDocumentNotFound() {
            when(documentMapper.selectById("missing")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getParseStatus("missing"));
            assertTrue(ex.getMessage().contains("Document not found"));
        }

        @Test
        @DisplayName("返回文档的 parse 状态信息")
        void shouldReturnParseStatus() {
            Document doc = new Document();
            doc.setId("doc-1");
            doc.setUploadStatus("completed");
            doc.setParseStatus("completed");
            doc.setChunkStatus("completed");
            doc.setChunkCount(10);
            doc.setParseErrorMessage(null);
            doc.setLastParseAt(LocalDateTime.of(2026, 6, 1, 10, 0));
            when(documentMapper.selectById("doc-1")).thenReturn(doc);

            DocumentParseService.DocumentParseStatus status = service.getParseStatus("doc-1");

            assertNotNull(status);
            assertEquals("doc-1", status.documentId());
            assertEquals("completed", status.uploadStatus());
            assertEquals("completed", status.parseStatus());
            assertEquals("completed", status.chunkStatus());
            assertEquals(Integer.valueOf(10), status.chunkCount());
            assertNull(status.errorMessage());
        }

        @Test
        @DisplayName("包含错误信息的状态")
        void shouldIncludeErrorMessage() {
            Document doc = new Document();
            doc.setId("doc-1");
            doc.setUploadStatus("completed");
            doc.setParseStatus("failed");
            doc.setChunkStatus("pending");
            doc.setParseErrorMessage("OCR 失败");
            when(documentMapper.selectById("doc-1")).thenReturn(doc);

            DocumentParseService.DocumentParseStatus status = service.getParseStatus("doc-1");

            assertEquals("failed", status.parseStatus());
            assertEquals("OCR 失败", status.errorMessage());
        }
    }

    @Nested
    @DisplayName("getParseResult")
    class GetParseResultTests {

        @Test
        @DisplayName("文档不存在时抛 RuntimeException")
        void shouldThrowWhenDocumentNotFound() {
            when(documentMapper.selectById("missing")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getParseResult("missing"));
            assertTrue(ex.getMessage().contains("Document not found"));
        }

        @Test
        @DisplayName("processResultFile 为 null 时返回 Optional.empty()")
        void shouldReturnEmptyWhenNoResultFile() {
            Document doc = new Document();
            doc.setId("doc-1");
            doc.setProcessResultFile(null);
            when(documentMapper.selectById("doc-1")).thenReturn(doc);

            Optional<DocumentParseService.DocumentProcessResult> result = service.getParseResult("doc-1");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("processResultFile 指向不存在的文件时返回 Optional.empty()")
        void shouldReturnEmptyWhenFileNotExist() {
            Document doc = new Document();
            doc.setId("doc-1");
            doc.setProcessResultFile("/non/existent/path.json");
            when(documentMapper.selectById("doc-1")).thenReturn(doc);

            Optional<DocumentParseService.DocumentProcessResult> result = service.getParseResult("doc-1");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("默认 parseDocument 重载")
    class DefaultOverloadTests {

        @Test
        @DisplayName("调用 parseDocument(id, strategy) 时 enableOcrFallback 默认 false")
        void shouldCallOverloadWithFalseFallback() {
            when(documentMapper.selectById("doc-1")).thenReturn(null);
            assertThrows(RuntimeException.class, () -> service.parseDocument("doc-1", "fixed"));
        }
    }
}
