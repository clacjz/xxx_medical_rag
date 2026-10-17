package com.clacjz.medical.rag.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextCleaner 单元测试")
class TextCleanerTest {

    private TextCleaner textCleaner;

    @BeforeEach
    void setUp() {
        textCleaner = new TextCleaner();
    }

    @Nested
    @DisplayName("clean - 文本清理")
    class CleanTests {

        @Test
        @DisplayName("null输入返回空字符串")
        void shouldReturnEmptyWhenNull() {
            assertEquals("", textCleaner.clean(null));
        }

        @Test
        @DisplayName("空白字符串返回空字符串")
        void shouldReturnEmptyWhenBlank() {
            assertEquals("", textCleaner.clean("   \t\n  "));
        }

        @Test
        @DisplayName("多空格合并为单空格")
        void shouldCollapseMultipleSpaces() {
            assertEquals("这 是 测试", textCleaner.clean("这    是   测试"));
        }

        @Test
        @DisplayName("多换行合并")
        void shouldCollapseMultipleNewlines() {
            String result = textCleaner.clean("第一段\n\n\n\n第二段");
            assertNotNull(result);
            assertFalse(result.contains("\n\n\n"));
        }

        @Test
        @DisplayName("多Tab合并为单Tab")
        void shouldCollapseMultipleTabs() {
            assertEquals("第一\t第二", textCleaner.clean("第一\t\t\t第二"));
        }

        @Test
        @DisplayName("去除首尾空白")
        void shouldTrimWhitespace() {
            assertEquals("文本", textCleaner.clean("  文本  "));
        }

        @Test
        @DisplayName("正常文本保持不变")
        void shouldPreserveNormalText() {
            assertEquals("这是正常的文本内容。", textCleaner.clean("这是正常的文本内容。"));
        }
    }

    @Nested
    @DisplayName("cleanLines - 批量清理行")
    class CleanLinesTests {

        @Test
        @DisplayName("null输入返回空列表")
        void shouldReturnEmptyWhenNull() {
            assertTrue(textCleaner.cleanLines(null).isEmpty());
        }

        @Test
        @DisplayName("过滤空白行")
        void shouldFilterBlankLines() {
            List<String> result = textCleaner.cleanLines(List.of("第一行", "  ", "第二行", "", "   "));
            assertEquals(2, result.size());
            assertEquals("第一行", result.get(0));
            assertEquals("第二行", result.get(1));
        }

        @Test
        @DisplayName("清理后返回非空行")
        void shouldCleanAndReturnNonBlankLines() {
            List<String> result = textCleaner.cleanLines(List.of("  第一行  ", "\t第二行\t"));
            assertEquals(2, result.size());
            assertEquals("第一行", result.get(0));
            assertEquals("第二行", result.get(1));
        }
    }

    @Nested
    @DisplayName("splitIntoParagraphs - 段落分割")
    class SplitIntoParagraphsTests {

        @Test
        @DisplayName("null输入返回空列表")
        void shouldReturnEmptyWhenNull() {
            assertTrue(textCleaner.splitIntoParagraphs(null).isEmpty());
        }

        @Test
        @DisplayName("空白输入返回空列表")
        void shouldReturnEmptyWhenBlank() {
            assertTrue(textCleaner.splitIntoParagraphs("   \n\n   ").isEmpty());
        }

        @Test
        @DisplayName("双换行分割为段落")
        void shouldSplitByDoubleNewline() {
            List<String> result = textCleaner.splitIntoParagraphs("第一段\n\n第二段\n\n第三段");
            assertEquals(3, result.size());
            assertEquals("第一段", result.get(0));
            assertEquals("第二段", result.get(1));
            assertEquals("第三段", result.get(2));
        }

        @Test
        @DisplayName("单段文本返回单元素列表")
        void shouldReturnSingleParagraph() {
            List<String> result = textCleaner.splitIntoParagraphs("这是单独的一段文本");
            assertEquals(1, result.size());
            assertEquals("这是单独的一段文本", result.get(0));
        }

        @Test
        @DisplayName("去除段落前后空白")
        void shouldTrimParagraphs() {
            List<String> result = textCleaner.splitIntoParagraphs("  第一段  \n\n  第二段  ");
            assertEquals("第一段", result.get(0));
            assertEquals("第二段", result.get(1));
        }
    }

    @Nested
    @DisplayName("splitIntoSentences - 句子分割")
    class SplitIntoSentencesTests {

        @Test
        @DisplayName("null输入返回空列表")
        void shouldReturnEmptyWhenNull() {
            assertTrue(textCleaner.splitIntoSentences(null).isEmpty());
        }

        @Test
        @DisplayName("空白输入返回空列表")
        void shouldReturnEmptyWhenBlank() {
            assertTrue(textCleaner.splitIntoSentences("   ").isEmpty());
        }

        @Test
        @DisplayName("中文句号分割")
        void shouldSplitByChinesePeriod() {
            List<String> result = textCleaner.splitIntoSentences("第一句。第二句。第三句。");
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("中英文句号混合分割")
        void shouldSplitByMixedPeriods() {
            List<String> result = textCleaner.splitIntoSentences("第一句。第二句！第三句？");
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("去除句子前后空白")
        void shouldTrimSentences() {
            List<String> result = textCleaner.splitIntoSentences("  第一句  。  第二句  ");
            assertEquals(2, result.size());
            assertEquals("第一句", result.get(0));
            assertEquals("第二句", result.get(1));
        }
    }

    @Nested
    @DisplayName("countCharacters - 字符计数")
    class CountCharactersTests {

        @Test
        @DisplayName("null输入返回0")
        void shouldReturnZeroWhenNull() {
            assertEquals(0, textCleaner.countCharacters(null));
        }

        @Test
        @DisplayName("返回实际字符数")
        void shouldReturnCharacterCount() {
            assertEquals(5, textCleaner.countCharacters("Hello"));
        }

        @Test
        @DisplayName("中文字符计数")
        void shouldCountChineseCharacters() {
            assertEquals(4, textCleaner.countCharacters("你好世界"));
        }
    }

    @Nested
    @DisplayName("countChineseCharacters - 中文字符计数")
    class CountChineseCharactersTests {

        @Test
        @DisplayName("null输入返回0")
        void shouldReturnZeroWhenNull() {
            assertEquals(0, textCleaner.countChineseCharacters(null));
        }

        @Test
        @DisplayName("纯中文返回完整计数")
        void shouldCountAllChineseChars() {
            assertEquals(2, textCleaner.countChineseCharacters("你好"));
        }

        @Test
        @DisplayName("纯英文返回0")
        void shouldReturnZeroForEnglish() {
            assertEquals(0, textCleaner.countChineseCharacters("hello"));
        }

        @Test
        @DisplayName("中英文混合只计中文")
        void shouldCountOnlyChinese() {
            // "hi你好世界hello" -> 你好世界 = 4个中文字符
            assertEquals(4, textCleaner.countChineseCharacters("hi你好世界hello"));
        }

        @Test
        @DisplayName("数字不计入中文")
        void shouldNotCountDigits() {
            // "hi你好123世界" -> 你好世界 = 4个中文字符
            assertEquals(4, textCleaner.countChineseCharacters("hi你好123世界"));
        }
    }
}