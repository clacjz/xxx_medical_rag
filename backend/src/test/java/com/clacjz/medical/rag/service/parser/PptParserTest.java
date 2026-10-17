package com.clacjz.medical.rag.service.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PptParser 单元测试")
class PptParserTest {

    @Mock
    private TextCleaner textCleaner;

    private PptParser parser;

    @BeforeEach
    void setUp() {
        when(textCleaner.clean(anyString())).thenAnswer(inv -> inv.getArgument(0));
        parser = new PptParser(textCleaner);
    }

    /** 构造一个 pptx 文件，包含两张幻灯片 */
    private byte[] buildPptxWithSlides(String... slideTexts) throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : slideTexts) {
                XSLFSlide slide = pptx.createSlide();
                XSLFTextBox textBox = slide.createTextBox();
                textBox.setText(text);
            }
            pptx.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("解析多 slide 的 pptx，输出 ## Slide N 标记")
    void shouldParseMultiSlidePptx() throws Exception {
        byte[] data = buildPptxWithSlides("First slide text", "Second slide text");

        String result = parser.parse(new ByteArrayInputStream(data));

        assertTrue(result.contains("## Slide 1"));
        assertTrue(result.contains("## Slide 2"));
        assertTrue(result.contains("First slide text"));
        assertTrue(result.contains("Second slide text"));
    }

    @Test
    @DisplayName("空 pptx 返回空字符串")
    void shouldHandleEmptyPptx() throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pptx.write(out);
            String result = parser.parse(new ByteArrayInputStream(out.toByteArray()));
            assertNotNull(result);
            // 没有 slide，所以没有 ## Slide N 标记
            assertFalse(result.contains("## Slide"));
        }
    }

    @Test
    @DisplayName("损坏的 pptx 抛 BizException")
    void shouldThrowOnCorruptStream() {
        InputStream bad = new ByteArrayInputStream("not a pptx".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(bad));
        assertTrue(ex.getMessage().contains("Failed to parse PPT file"));
    }

    @Test
    @DisplayName("getSupportedExtensions 包含 pptx 和 ppt")
    void shouldReturnSupportedExtensions() {
        String[] exts = parser.getSupportedExtensions();
        assertArrayEquals(new String[]{"pptx", "ppt"}, exts);
    }

    @Nested
    @DisplayName("supports (继承自DocumentParser)")
    class SupportsTests {

        @Test
        @DisplayName("支持 pptx 扩展名")
        void shouldSupportPptx() {
            assertTrue(parser.supports("pptx"));
        }

        @Test
        @DisplayName("支持 ppt 扩展名")
        void shouldSupportPpt() {
            assertTrue(parser.supports("ppt"));
        }

        @Test
        @DisplayName("支持大写扩展名")
        void shouldSupportUpperCase() {
            assertTrue(parser.supports("PPTX"));
            assertTrue(parser.supports("PPT"));
        }

        @Test
        @DisplayName("支持带点前缀的扩展名")
        void shouldSupportDotPrefix() {
            assertTrue(parser.supports(".pptx"));
            assertTrue(parser.supports(".ppt"));
        }

        @Test
        @DisplayName("不支持无关扩展名")
        void shouldNotSupportOtherExtensions() {
            assertFalse(parser.supports("pdf"));
            assertFalse(parser.supports("docx"));
        }
    }

    @Nested
    @DisplayName("TextCleaner集成")
    class TextCleanerIntegrationTests {

        @Test
        @DisplayName("解析时调用TextCleaner进行清理")
        void shouldCallTextCleaner() throws Exception {
            when(textCleaner.clean(anyString())).thenReturn("cleaned");
            byte[] data = buildPptxWithSlides("raw text");

            parser.parse(new ByteArrayInputStream(data));

            verify(textCleaner, atLeastOnce()).clean(anyString());
        }
    }
}
