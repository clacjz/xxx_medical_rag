package com.clacjz.medical.rag.service.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
@DisplayName("ExcelParser 单元测试")
class ExcelParserTest {

    @Mock
    private TextCleaner textCleaner;

    private ExcelParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new ExcelParser(textCleaner);
    }

    private byte[] buildXlsWithSheet(String sheetName, String[][] rows) throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("解析包含 sheet name 和多行数据的 xls 文件")
    void shouldParseXlsWithSheetName() throws Exception {
        byte[] data = buildXlsWithSheet("Sheet1", new String[][]{
                {"姓名", "年龄"},
                {"张三", "30"},
                {"李四", "25"}
        });

        String result = parser.parse(new ByteArrayInputStream(data));

        assertTrue(result.contains("## Sheet: Sheet1"));
        assertTrue(result.contains("姓名"));
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("30"));
    }

    @Test
    @DisplayName("空单元格被过滤")
    void shouldSkipEmptyCells() throws Exception {
        byte[] data = buildXlsWithSheet("S", new String[][]{
                {"a", "", "c"},
                {"", "b", ""}
        });

        String result = parser.parse(new ByteArrayInputStream(data));

        assertTrue(result.contains("a"));
        assertTrue(result.contains("c"));
        assertTrue(result.contains("b"));
        // 不应出现连续 || 之类的
        assertFalse(result.contains("| |"));
    }

    @Test
    @DisplayName("空 sheet 不报错")
    void shouldHandleEmptySheet() throws Exception {
        byte[] data = buildXlsWithSheet("Empty", new String[][]{ });

        String result = parser.parse(new ByteArrayInputStream(data));

        assertNotNull(result);
    }

    @Test
    @DisplayName("损坏的流抛 RuntimeException")
    void shouldThrowOnCorruptStream() {
        InputStream bad = new ByteArrayInputStream("not xls".getBytes());
        assertThrows(RuntimeException.class, () -> parser.parse(bad));
    }

    @Test
    @DisplayName("getSupportedExtensions 包含 xlsx 和 xls")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"xlsx", "xls"}, exts);
    }

    @Nested
    @DisplayName("supports (继承自DocumentParser)")
    class SupportsTests {

        @Test
        @DisplayName("支持 xlsx 扩展名")
        void shouldSupportXlsx() {
            assertTrue(parser.supports("xlsx"));
        }

        @Test
        @DisplayName("支持 xls 扩展名")
        void shouldSupportXls() {
            assertTrue(parser.supports("xls"));
        }

        @Test
        @DisplayName("支持大写扩展名")
        void shouldSupportUpperCase() {
            assertTrue(parser.supports("XLSX"));
            assertTrue(parser.supports("XLS"));
        }

        @Test
        @DisplayName("支持带点前缀的扩展名")
        void shouldSupportDotPrefix() {
            assertTrue(parser.supports(".xlsx"));
            assertTrue(parser.supports(".xls"));
        }

        @Test
        @DisplayName("不支持无关扩展名")
        void shouldNotSupportOtherExtensions() {
            assertFalse(parser.supports("pdf"));
            assertFalse(parser.supports("docx"));
        }
    }
}
