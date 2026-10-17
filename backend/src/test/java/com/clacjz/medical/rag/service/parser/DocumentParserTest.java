package com.clacjz.medical.rag.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentParser 接口默认方法测试")
class DocumentParserTest {

    /**
     * Minimal stub implementation for testing the default supports() method.
     */
    private static class StubParser implements DocumentParser {

        private final String[] extensions;

        StubParser(String... extensions) {
            this.extensions = extensions;
        }

        @Override
        public String parse(InputStream inputStream) {
            return "stub";
        }

        @Override
        public String[] getSupportedExtensions() {
            return extensions;
        }
    }

    @Nested
    @DisplayName("supports")
    class SupportsTests {

        @Test
        @DisplayName("匹配已注册的扩展名")
        void shouldMatchRegisteredExtension() {
            DocumentParser parser = new StubParser("pdf", "docx");

            assertTrue(parser.supports("pdf"));
            assertTrue(parser.supports("docx"));
        }

        @Test
        @DisplayName("不匹配未注册的扩展名")
        void shouldNotMatchUnregisteredExtension() {
            DocumentParser parser = new StubParser("pdf", "docx");

            assertFalse(parser.supports("xlsx"));
            assertFalse(parser.supports("txt"));
        }

        @Test
        @DisplayName("大小写不敏感匹配")
        void shouldMatchCaseInsensitive() {
            DocumentParser parser = new StubParser("pdf");

            assertTrue(parser.supports("PDF"));
            assertTrue(parser.supports("Pdf"));
            assertTrue(parser.supports("pDf"));
        }

        @Test
        @DisplayName("支持带点前缀的扩展名")
        void shouldSupportDotPrefixedExtension() {
            DocumentParser parser = new StubParser("pdf");

            assertTrue(parser.supports(".pdf"));
        }

        @Test
        @DisplayName("空扩展名不应匹配")
        void shouldNotMatchEmptyExtension() {
            DocumentParser parser = new StubParser("pdf");

            assertFalse(parser.supports(""));
        }

        @Test
        @DisplayName("null扩展名列表的解析器不应匹配任何扩展名")
        void shouldNotMatchWithEmptyExtensions() {
            DocumentParser parser = new StubParser();

            assertFalse(parser.supports("pdf"));
        }

        @Test
        @DisplayName("多个点号的扩展名只匹配最后一段")
        void shouldHandleMultipleDots() {
            DocumentParser parser = new StubParser("pdf");

            // ".pdf" -> strip leading dot -> "pdf" -> matches
            assertTrue(parser.supports(".pdf"));
        }
    }

    @Nested
    @DisplayName("ParseResult record")
    class ParseResultTests {

        @Test
        @DisplayName("应正确创建ParseResult")
        void shouldCreateParseResult() {
            var page = new DocumentParser.ParseResult.PageContent(1, "text", 4);
            DocumentParser.ParseResult result = new DocumentParser.ParseResult(
                    "content", List.of(page), 1, 7, List.of("warning1")
            );

            assertEquals("content", result.content());
            assertEquals(1, result.pages().size());
            assertEquals(1, result.totalPages());
            assertEquals(7, result.charCount());
            assertEquals(1, result.warnings().size());
            assertEquals("warning1", result.warnings().get(0));
        }

        @Test
        @DisplayName("PageContent应正确存储页码和文本")
        void shouldCreatePageContent() {
            var page = new DocumentParser.ParseResult.PageContent(5, "page text", 9);

            assertEquals(5, page.pageNum());
            assertEquals("page text", page.text());
            assertEquals(9, page.charCount());
        }

        @Test
        @DisplayName("ParseResult空列表应正常工作")
        void shouldHandleEmptyLists() {
            DocumentParser.ParseResult result = new DocumentParser.ParseResult(
                    "content", List.of(), 0, 7, List.of()
            );

            assertTrue(result.pages().isEmpty());
            assertTrue(result.warnings().isEmpty());
            assertEquals(0, result.totalPages());
        }
    }
}
