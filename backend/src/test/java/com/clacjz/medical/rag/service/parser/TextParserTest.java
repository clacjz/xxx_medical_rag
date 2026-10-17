package com.clacjz.medical.rag.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TextParser 单元测试")
class TextParserTest {

    @Mock
    private TextCleaner textCleaner;

    private TextParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new TextParser(textCleaner);
    }

    @Test
    @DisplayName("解析多行文本并保留换行")
    void shouldParseMultilineText() {
        String content = "line one\nline two\nline three";
        InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertEquals("line one\nline two\nline three", result);
    }

    @Test
    @DisplayName("空输入返回空字符串")
    void shouldHandleEmptyStream() {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        String result = parser.parse(in);
        assertEquals("", result);
    }

    @Test
    @DisplayName("支持扩展名: txt, text")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"txt", "text"}, exts);
    }

    @Test
    @DisplayName("supports 默认实现匹配扩展名")
    void shouldSupportExtensionsViaDefaultMethod() {
        assertTrue(parser.supports("txt"));
        assertTrue(parser.supports("text"));
        assertTrue(parser.supports(".TXT")); // 大小写不敏感
        assertFalse(parser.supports("pdf"));
    }

    @Test
    @DisplayName("cleaner 处理后的内容作为返回")
    void shouldReturnCleanedText() {
        when(textCleaner.clean(anyString())).thenReturn("cleaned");
        InputStream in = new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8));
        assertEquals("cleaned", parser.parse(in));
    }

    @Test
    @DisplayName("流读取失败抛 RuntimeException")
    void shouldThrowWhenStreamFails() {
        InputStream bad = new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("disk error");
            }
        };

        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(bad));
        assertTrue(ex.getMessage().contains("Failed to parse text file"));
    }
}
