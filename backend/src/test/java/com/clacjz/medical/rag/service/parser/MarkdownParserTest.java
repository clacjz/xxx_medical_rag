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
@DisplayName("MarkdownParser 单元测试")
class MarkdownParserTest {

    @Mock
    private TextCleaner textCleaner;

    private MarkdownParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new MarkdownParser(textCleaner);
    }

    @Test
    @DisplayName("代码块替换为空格")
    void shouldRemoveCodeBlock() {
        String md = "before\n```java\ncode here\n```\nafter";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertFalse(result.contains("code here"));
        assertTrue(result.contains("before"));
        assertTrue(result.contains("after"));
    }

    @Test
    @DisplayName("内联代码替换为空格")
    void shouldRemoveInlineCode() {
        String md = "use `foo()` here";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertFalse(result.contains("`foo()`"));
        assertTrue(result.contains("use"));
    }

    @Test
    @DisplayName("Markdown 链接 [text](url) 保留 text")
    void shouldKeepLinkText() {
        String md = "click [百度](https://baidu.com) here";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("百度"));
        assertFalse(result.contains("https://baidu.com"));
    }

    @Test
    @DisplayName("图片 ![alt](url) 保留 alt")
    void shouldKeepImageAlt() {
        String md = "![logo](logo.png) text";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("logo"));
        assertFalse(result.contains("logo.png"));
    }

    @Test
    @DisplayName("标题 # 符号被去除")
    void shouldRemoveHeadingMarker() {
        String md = "# Title\n## Subtitle\ncontent";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("Title"));
        assertTrue(result.contains("Subtitle"));
        assertTrue(result.contains("content"));
        assertFalse(result.contains("# Title"));
    }

    @Test
    @DisplayName("粗体/斜体/下划线 markdown 标记去除")
    void shouldRemoveEmphasisMarkers() {
        String md = "**bold** *italic* __under__ _und_";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("bold"));
        assertTrue(result.contains("italic"));
        assertTrue(result.contains("under"));
        assertTrue(result.contains("und"));
        assertFalse(result.contains("**bold**"));
    }

    @Test
    @DisplayName("无序列表 - * + 转为统一 -")
    void shouldNormalizeBulletLists() {
        String md = "- a\n* b\n+ c";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("- a"));
        assertTrue(result.contains("- b"));
        assertTrue(result.contains("- c"));
    }

    @Test
    @DisplayName("有序列表 1./2./3. 转为统一 1.")
    void shouldNormalizeOrderedLists() {
        String md = "1. first\n2. second\n3. third";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertTrue(result.contains("1. first"));
        assertTrue(result.contains("1. second"));
        assertTrue(result.contains("1. third"));
    }

    @Test
    @DisplayName("--- 分割线转为换行")
    void shouldConvertHorizontalRule() {
        String md = "a\n---\nb";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertFalse(result.contains("---"));
    }

    @Test
    @DisplayName("表格 | 替换为空格")
    void shouldReplaceTablePipe() {
        String md = "a | b | c";
        InputStream in = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

        String result = parser.parse(in);

        assertFalse(result.contains("|"));
    }

    @Test
    @DisplayName("支持扩展名: md, markdown")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"md", "markdown"}, exts);
    }

    @Test
    @DisplayName("空输入返回空")
    void shouldHandleEmptyStream() {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        String result = parser.parse(in);
        assertEquals("", result);
    }

    @Test
    @DisplayName("cleaner 处理后的内容作为返回")
    void shouldReturnCleanedText() {
        when(textCleaner.clean(anyString())).thenReturn("cleaned");
        InputStream in = new ByteArrayInputStream("# Title".getBytes(StandardCharsets.UTF_8));
        assertEquals("cleaned", parser.parse(in));
    }

    @Test
    @DisplayName("流读取失败抛 RuntimeException")
    void shouldThrowOnStreamError() {
        InputStream bad = new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("io error");
            }
        };

        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(bad));
        assertTrue(ex.getMessage().contains("Failed to parse markdown file"));
    }
}
