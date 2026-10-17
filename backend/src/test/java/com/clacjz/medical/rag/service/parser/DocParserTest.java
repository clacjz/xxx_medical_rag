package com.clacjz.medical.rag.service.parser;

import com.clacjz.medical.rag.exception.BizException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocParser 单元测试")
class DocParserTest {

    @Mock
    private TextCleaner textCleaner;

    private DocParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new DocParser(textCleaner);
    }

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("损坏的流应抛出BizException")
        void shouldThrowBizExceptionOnCorruptStream() {
            InputStream badStream = new ByteArrayInputStream("not a doc file".getBytes());

            BizException ex = assertThrows(BizException.class, () -> parser.parse(badStream));
            assertTrue(ex.getMessage().contains("Failed to parse DOC file"));
        }

        @Test
        @DisplayName("null 输入流应抛出BizException")
        void shouldThrowBizExceptionOnNullStream() {
            assertThrows(BizException.class, () -> parser.parse(null));
        }

        @Test
        @DisplayName("非OLE2格式文件应抛出BizException")
        void shouldThrowBizExceptionOnNonOle2File() {
            // Plain text is not a valid .doc (OLE2) format
            InputStream textStream = new ByteArrayInputStream("Hello World".getBytes());

            assertThrows(BizException.class, () -> parser.parse(textStream));
        }

        @Test
        @DisplayName("空输入流应抛出BizException")
        void shouldThrowBizExceptionOnEmptyStream() {
            InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

            assertThrows(BizException.class, () -> parser.parse(emptyStream));
        }
    }

    @Nested
    @DisplayName("getSupportedExtensions")
    class GetSupportedExtensionsTests {

        @Test
        @DisplayName("应返回 doc 扩展名")
        void shouldReturnDocExtension() {
            String[] exts = parser.getSupportedExtensions();

            assertArrayEquals(new String[]{"doc"}, exts);
        }
    }

    @Nested
    @DisplayName("supports (inherited from DocumentParser)")
    class SupportsTests {

        @Test
        @DisplayName("应支持 doc 扩展名")
        void shouldSupportDocExtension() {
            assertTrue(parser.supports("doc"));
        }

        @Test
        @DisplayName("应支持带点前缀的 doc 扩展名")
        void shouldSupportDocExtensionWithDot() {
            assertTrue(parser.supports(".doc"));
        }

        @Test
        @DisplayName("应支持大写 DOC 扩展名")
        void shouldSupportDocExtensionUpperCase() {
            assertTrue(parser.supports("DOC"));
        }

        @Test
        @DisplayName("不应支持 docx 扩展名")
        void shouldNotSupportDocxExtension() {
            assertFalse(parser.supports("docx"));
        }

        @Test
        @DisplayName("不应支持空字符串")
        void shouldNotSupportEmptyString() {
            assertFalse(parser.supports(""));
        }
    }
}
