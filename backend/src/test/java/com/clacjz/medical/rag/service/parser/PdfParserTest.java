package com.clacjz.medical.rag.service.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PdfParser 单元测试")
class PdfParserTest {

    @Mock
    private TextCleaner textCleaner;

    private PdfParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new PdfParser(textCleaner);
    }

    /** 使用 PDFBox 构造一个含多页的测试 PDF，第一页含 "Hello Page 1"，第二页含 "Page 2 text" */
    private byte[] buildMultiPagePdf() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 第一页
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello Page 1");
                cs.endText();
            }
            // 第二页
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Page 2 text");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("解析多页 PDF 返回合并文本")
    void shouldParseMultiPagePdf() throws Exception {
        byte[] data = buildMultiPagePdf();
        String result = parser.parse(new ByteArrayInputStream(data));

        assertNotNull(result);
        assertTrue(result.contains("Hello Page 1"));
        assertTrue(result.contains("Page 2 text"));
    }

    @Test
    @DisplayName("parseWithLayout 返回每页的 PageLayout 列表")
    void shouldReturnLayoutsForEachPage() throws Exception {
        byte[] data = buildMultiPagePdf();
        InputStream in = new ByteArrayInputStream(data);

        List<PageLayout> layouts = parser.parseWithLayout(in);

        assertEquals(2, layouts.size());
        assertEquals(1, layouts.get(0).pageNumber());
        assertEquals(2, layouts.get(1).pageNumber());
        assertTrue(layouts.get(0).text().contains("Hello Page 1"));
        assertTrue(layouts.get(1).text().contains("Page 2 text"));
    }

    @Test
    @DisplayName("PageLayout 的 startPos/endPos 反映累计位置")
    void shouldTrackCumulativePositions() throws Exception {
        byte[] data = buildMultiPagePdf();
        List<PageLayout> layouts = parser.parseWithLayout(new ByteArrayInputStream(data));

        // 第一页 startPos = 0
        assertEquals(0, layouts.get(0).startPos());
        // 第二页 startPos = 第一页文本长度 + "\n\n"
        assertTrue(layouts.get(1).startPos() > layouts.get(0).endPos());
    }

    @Test
    @DisplayName("空 PDF 仍然返回空布局列表")
    void shouldHandleEmptyPdf() throws Exception {
        try (PDDocument doc = new ByteArrayOutputStream() instanceof ByteArrayOutputStream ? null : null ;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 实际构造空 PDF
        }
        // 用真实空 PDF
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            List<PageLayout> layouts = parser.parseWithLayout(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(0, layouts.size());
        }
    }

    @Test
    @DisplayName("损坏的 PDF 数据抛 RuntimeException")
    void shouldThrowOnCorruptStream() {
        InputStream bad = new ByteArrayInputStream("not a pdf".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(bad));
        assertTrue(ex.getMessage().contains("Failed to parse PDF file"));
    }

    @Test
    @DisplayName("getSupportedExtensions 包含 pdf")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"pdf"}, exts);
    }

    @Nested
    @DisplayName("supports (继承自DocumentParser)")
    class SupportsTests {

        @Test
        @DisplayName("支持 pdf 扩展名")
        void shouldSupportPdf() {
            assertTrue(parser.supports("pdf"));
        }

        @Test
        @DisplayName("支持大写扩展名")
        void shouldSupportUpperCase() {
            assertTrue(parser.supports("PDF"));
        }

        @Test
        @DisplayName("支持带点前缀的扩展名")
        void shouldSupportDotPrefix() {
            assertTrue(parser.supports(".pdf"));
        }

        @Test
        @DisplayName("不支持无关扩展名")
        void shouldNotSupportOtherExtensions() {
            assertFalse(parser.supports("docx"));
            assertFalse(parser.supports("xlsx"));
        }
    }
}
