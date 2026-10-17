package com.clacjz.medical.rag.service.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WordParser 单元测试")
class WordParserTest {

    @Mock
    private TextCleaner textCleaner;

    private WordParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new WordParser(textCleaner);
    }

    private byte[] buildDocxWithParagraphs(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                XWPFParagraph para = doc.createParagraph();
                para.createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithTable(String[][] tableData) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable();
            for (int i = 0; i < tableData.length; i++) {
                XWPFTableRow row = (i == 0) ? table.getRow(0) : table.createRow();
                for (int c = 0; c < tableData[i].length; c++) {
                    XWPFTableCell cell = (c < row.getTableCells().size()) ? row.getCell(c) : row.addNewTableCell();
                    cell.setText(tableData[i][c]);
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("解析包含多个段落的 docx")
    void shouldParseParagraphs() throws Exception {
        byte[] data = buildDocxWithParagraphs("第一段", "第二段", "第三段");

        String result = parser.parse(new ByteArrayInputStream(data));

        assertTrue(result.contains("第一段"));
        assertTrue(result.contains("第二段"));
        assertTrue(result.contains("第三段"));
    }

    @Test
    @DisplayName("包含表格的 docx 解析")
    void shouldParseTable() throws Exception {
        byte[] data = buildDocxWithTable(new String[][]{
                {"A1", "B1"},
                {"A2", "B2"}
        });

        String result = parser.parse(new ByteArrayInputStream(data));

        assertTrue(result.contains("[Table 1]"));
        assertTrue(result.contains("A1"));
        assertTrue(result.contains("B2"));
    }

    @Test
    @DisplayName("空文档返回空字符串")
    void shouldHandleEmptyDocument() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            String result = parser.parse(new ByteArrayInputStream(out.toByteArray()));
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("损坏的 docx 抛 RuntimeException")
    void shouldThrowOnCorruptStream() {
        InputStream bad = new ByteArrayInputStream("not a docx".getBytes());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(bad));
        assertTrue(ex.getMessage().contains("Failed to parse Word file"));
    }

    @Test
    @DisplayName("getSupportedExtensions 包含 docx")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"docx"}, exts);
    }

    @Nested
    @DisplayName("supports (继承自DocumentParser)")
    class SupportsTests {

        @Test
        @DisplayName("支持 docx 扩展名")
        void shouldSupportDocx() {
            assertTrue(parser.supports("docx"));
        }

        @Test
        @DisplayName("支持大写扩展名")
        void shouldSupportUpperCase() {
            assertTrue(parser.supports("DOCX"));
        }

        @Test
        @DisplayName("支持带点前缀的扩展名")
        void shouldSupportDotPrefix() {
            assertTrue(parser.supports(".docx"));
        }

        @Test
        @DisplayName("不支持 doc 扩展名")
        void shouldNotSupportDoc() {
            assertFalse(parser.supports("doc"));
        }

        @Test
        @DisplayName("不支持无关扩展名")
        void shouldNotSupportOtherExtensions() {
            assertFalse(parser.supports("pdf"));
            assertFalse(parser.supports("xlsx"));
        }
    }
}
