package com.clacjz.medical.rag.service.parser;

import com.clacjz.medical.rag.config.ParserProperties;
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
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentParserFactory 单元测试")
class DocumentParserFactoryTest {

    @Mock
    private ParserProperties parserProperties;

    private DocumentParserFactory factory;

    @BeforeEach
    void setUp() {
        when(parserProperties.getSupportedTypes()).thenReturn(new String[]{"pdf", "docx", "txt", "md", "xlsx"});
        DocumentParser txtParser = new TextParser(new TextCleaner());
        DocumentParser mdParser = new MarkdownParser(new TextCleaner());
        factory = new DocumentParserFactory(List.of(txtParser, mdParser), parserProperties);
        factory.init();
    }

    @Nested
    @DisplayName("getParser 路由")
    class GetParserTests {

        @Test
        @DisplayName("txt 文件路由到 TextParser")
        void shouldRouteTxt() {
            DocumentParser parser = factory.getParser("foo.txt");
            assertInstanceOf(TextParser.class, parser);
        }

        @Test
        @DisplayName("TEXT 大写扩展名也路由到 TextParser")
        void shouldBeCaseInsensitive() {
            DocumentParser parser = factory.getParser("foo.TEXT");
            assertInstanceOf(TextParser.class, parser);
        }

        @Test
        @DisplayName("md 文件路由到 MarkdownParser")
        void shouldRouteMd() {
            DocumentParser parser = factory.getParser("doc.md");
            assertInstanceOf(MarkdownParser.class, parser);
        }

        @Test
        @DisplayName("markdown 扩展名也路由到 MarkdownParser")
        void shouldRouteMarkdown() {
            DocumentParser parser = factory.getParser("doc.markdown");
            assertInstanceOf(MarkdownParser.class, parser);
        }

        @Test
        @DisplayName("路径中带目录也能提取扩展名")
        void shouldHandleFilePath() {
            DocumentParser parser = factory.getParser("/some/dir/file.txt");
            assertInstanceOf(TextParser.class, parser);
        }

        @Test
        @DisplayName("未知扩展名抛 UnsupportedOperationException 且消息含 supported types")
        void shouldThrowForUnknownExtension() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> factory.getParser("doc.xyz"));
            assertTrue(ex.getMessage().contains("xyz"));
            assertTrue(ex.getMessage().contains("Supported types"));
        }

        @Test
        @DisplayName("null 文件名抛 IllegalArgumentException")
        void shouldThrowOnNullFileName() {
            assertThrows(IllegalArgumentException.class, () -> factory.getParser(null));
        }

        @Test
        @DisplayName("空白文件名抛 IllegalArgumentException")
        void shouldThrowOnBlankFileName() {
            assertThrows(IllegalArgumentException.class, () -> factory.getParser("   "));
        }

        @Test
        @DisplayName("无扩展名文件抛 UnsupportedOperationException")
        void shouldThrowWhenNoExtension() {
            assertThrows(UnsupportedOperationException.class, () -> factory.getParser("README"));
        }

        @Test
        @DisplayName("文件名以 . 结尾抛 UnsupportedOperationException")
        void shouldThrowWhenTrailingDot() {
            assertThrows(UnsupportedOperationException.class, () -> factory.getParser("foo."));
        }
    }

    @Nested
    @DisplayName("isSupported")
    class IsSupportedTests {

        @Test
        @DisplayName("支持的文件返回 true")
        void shouldReturnTrueForSupported() {
            assertTrue(factory.isSupported("foo.txt"));
            assertTrue(factory.isSupported("foo.md"));
        }

        @Test
        @DisplayName("不支持的文件返回 false")
        void shouldReturnFalseForUnsupported() {
            assertFalse(factory.isSupported("foo.xyz"));
        }

        @Test
        @DisplayName("null 文件名返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(factory.isSupported(null));
        }

        @Test
        @DisplayName("空白文件名返回 false")
        void shouldReturnFalseForBlank() {
            assertFalse(factory.isSupported("   "));
        }

        @Test
        @DisplayName("无扩展名文件返回 false")
        void shouldReturnFalseForNoExtension() {
            assertFalse(factory.isSupported("README"));
        }
    }

    @Nested
    @DisplayName("parse 便捷方法")
    class ParseTests {

        @Test
        @DisplayName("委托给对应 parser")
        void shouldDelegateToParser() {
            InputStream in = new ByteArrayInputStream("hello world".getBytes());
            String result = factory.parse("foo.txt", in);
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("未知文件抛 UnsupportedOperationException")
        void shouldThrowForUnknown() {
            InputStream in = new ByteArrayInputStream(new byte[0]);
            assertThrows(UnsupportedOperationException.class, () -> factory.parse("foo.bin", in));
        }
    }

    @Nested
    @DisplayName("parseWithLayout 页面布局")
    class ParseWithLayoutTests {

        @Test
        @DisplayName("非 PDF 文件返回空列表")
        void shouldReturnEmptyForNonPdf() {
            InputStream in = new ByteArrayInputStream("plain".getBytes());
            List<PageLayout> result = factory.parseWithLayout("foo.txt", in);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getSupportedExtensions")
    class GetSupportedExtensionsTests {

        @Test
        @DisplayName("返回注册的扩展名列表")
        void shouldReturnRegisteredExtensions() {
            List<String> exts = factory.getSupportedExtensions();
            assertTrue(exts.contains("txt"));
            assertTrue(exts.contains("text"));
            assertTrue(exts.contains("md"));
            assertTrue(exts.contains("markdown"));
        }

        @Test
        @DisplayName("返回不可变副本")
        void shouldReturnImmutableCopy() {
            List<String> exts = factory.getSupportedExtensions();
            assertThrows(UnsupportedOperationException.class, () -> exts.add("xyz"));
        }
    }
}
