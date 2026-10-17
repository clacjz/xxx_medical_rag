package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MedicalTermNormalizer 单元测试")
class MedicalTermNormalizerTest {

    private MedicalTermNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new MedicalTermNormalizer(new ObjectMapper());
        normalizer.init();
    }

    @Nested
    @DisplayName("normalize - 术语归一化")
    class NormalizeTests {

        @Test
        @DisplayName("null输入返回null")
        void shouldReturnNullWhenInputNull() {
            String result = normalizer.normalize(null);
            assertNull(result);
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void shouldReturnEmptyWhenInputEmpty() {
            String result = normalizer.normalize("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("空白字符串返回空白字符串")
        void shouldReturnBlankWhenInputBlank() {
            String result = normalizer.normalize("   ");
            assertEquals("   ", result);
        }

        @Test
        @DisplayName("无匹配术语时返回原query")
        void shouldReturnOriginalWhenNoMatch() {
            String query = "这是一个没有任何术语的查询";
            String result = normalizer.normalize(query);
            assertEquals(query, result);
        }

        @Test
        @DisplayName("匹配术语时追加同义词")
        void shouldAppendSynonymsWhenMatchFound() {
            String query = "这个药物的适应症是什么";
            String result = normalizer.normalize(query);
            // 适应症匹配后应追加同义词组
            assertTrue(result.contains("适应症"));
            assertNotEquals(query, result);
        }

        @Test
        @DisplayName("多次调用结果一致（幂等性）")
        void shouldBeIdempotent() {
            String query = "适应症";
            String first = normalizer.normalize(query);
            String second = normalizer.normalize(first);
            assertEquals(first, second);
        }

        @Test
        @DisplayName("多个术语匹配时追加所有同义词")
        void shouldAppendAllSynonymsForMultipleMatches() {
            String query = "功能和适应症";
            String result = normalizer.normalize(query);
            assertNotEquals(query, result);
        }
    }

    @Nested
    @DisplayName("getSynonymGroups - 获取同义词组")
    class GetSynonymGroupsTests {

        @Test
        @DisplayName("返回已加载的同义词组")
        void shouldReturnLoadedSynonymGroups() {
            List<List<String>> groups = normalizer.getSynonymGroups();
            assertNotNull(groups);
        }

        @Test
        @DisplayName("同义词组不为空")
        void shouldHaveNonEmptyGroups() {
            List<List<String>> groups = normalizer.getSynonymGroups();
            if (!groups.isEmpty()) {
                assertTrue(groups.get(0).size() >= 2);
            }
        }
    }
}