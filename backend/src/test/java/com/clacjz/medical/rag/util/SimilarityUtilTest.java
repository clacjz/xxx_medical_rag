package com.clacjz.medical.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimilarityUtil 单元测试")
class SimilarityUtilTest {

    @Nested
    @DisplayName("jaccardSimilarity")
    class JaccardSimilarityTests {

        @Test
        @DisplayName("完全相同字符串返回 1.0")
        void shouldReturn1ForIdenticalStrings() {
            assertEquals(1.0, SimilarityUtil.jaccardSimilarity("hello", "hello"));
        }

        @Test
        @DisplayName("完全不同字符返回 0.0")
        void shouldReturn0ForDisjointStrings() {
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity("abc", "xyz"));
        }

        @Test
        @DisplayName("部分重叠返回中间值")
        void shouldReturnPartialScore() {
            // {a, b, c} ∩ {a, b, d} = {a, b}, |union| = {a, b, c, d} = 4
            double sim = SimilarityUtil.jaccardSimilarity("abc", "abd");
            assertEquals(0.5, sim, 0.001);
        }

        @Test
        @DisplayName("空字符串返回 0.0")
        void shouldReturn0ForEmpty() {
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity("", ""));
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity("abc", ""));
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity("", "abc"));
        }

        @Test
        @DisplayName("null 返回 0.0")
        void shouldReturn0ForNull() {
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity(null, "abc"));
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity("abc", null));
            assertEquals(0.0, SimilarityUtil.jaccardSimilarity(null, null));
        }

        @Test
        @DisplayName("字符顺序不影响集合相似度")
        void shouldIgnoreCharOrder() {
            assertEquals(SimilarityUtil.jaccardSimilarity("abc", "abc"),
                    SimilarityUtil.jaccardSimilarity("cba", "bac"));
        }

        @Test
        @DisplayName("重复字符不影响")
        void shouldIgnoreDuplicates() {
            assertEquals(1.0, SimilarityUtil.jaccardSimilarity("aaa", "a"));
        }
    }

    @Nested
    @DisplayName("isSimilar")
    class IsSimilarTests {

        @Test
        @DisplayName("相似度高于阈值返回 true")
        void shouldReturnTrueWhenSimilar() {
            assertTrue(SimilarityUtil.isSimilar("hello", "hello", 0.5));
        }

        @Test
        @DisplayName("相似度低于阈值返回 false")
        void shouldReturnFalseWhenNotSimilar() {
            assertFalse(SimilarityUtil.isSimilar("abc", "xyz", 0.5));
        }

        @Test
        @DisplayName("阈值 0 时任何非空字符串对都相似")
        void shouldReturnTrueForZeroThreshold() {
            assertTrue(SimilarityUtil.isSimilar("abc", "xyz", 0.0));
        }

        @Test
        @DisplayName("阈值 1 时只有完全相同才算相似")
        void shouldReturnFalseForFullThreshold() {
            assertFalse(SimilarityUtil.isSimilar("abc", "abx", 1.0));
            assertTrue(SimilarityUtil.isSimilar("abc", "abc", 1.0));
        }
    }
}
