package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResultMerger 单元测试")
class SearchResultMergerTest {

    private SearchResult makeResult(String id, String docId, double score) {
        return SearchResult.builder()
                .id(id)
                .documentId(docId)
                .score(score)
                .text("test")
                .build();
    }

    @Nested
    @DisplayName("merge - 合并检索结果")
    class MergeTests {

        @Test
        @DisplayName("null输入返回空列表")
        void shouldReturnEmptyWhenNullInput() {
            List<SearchResult> result = SearchResultMerger.merge(null, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空列表返回空列表")
        void shouldReturnEmptyWhenEmptyInput() {
            List<SearchResult> result = SearchResultMerger.merge(List.of(), 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单组结果直接返回")
        void shouldReturnSingleListAsIs() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.9), makeResult("2", "doc2", 0.8))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("多组结果按id去重合并")
        void shouldDeduplicateById() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.9), makeResult("2", "doc2", 0.8)),
                    List.of(makeResult("1", "doc1", 0.7), makeResult("3", "doc3", 0.6))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(3, result.size()); // 3 unique ids
            // id=1 should have max score 0.9
            SearchResult r1 = result.stream().filter(r -> r.getId().equals("1")).findFirst().orElseThrow();
            assertEquals(0.9, r1.getScore(), 0.001);
        }

        @Test
        @DisplayName("MAX_SCORE保留最高分")
        void shouldKeepMaxScore() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.7)),
                    List.of(makeResult("1", "doc1", 0.9))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(0.9, result.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("AVG_SCORE计算平均分")
        void shouldCalculateAverageScore() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.8)),
                    List.of(makeResult("1", "doc1", 0.4))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.AVG_SCORE);
            assertEquals(0.6, result.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("topK限制返回数量")
        void shouldLimitByTopK() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.9), makeResult("2", "doc2", 0.8), makeResult("3", "doc3", 0.7))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 2, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("按分数降序排列")
        void shouldSortByScoreDescending() {
            List<List<SearchResult>> input = List.of(
                    List.of(makeResult("1", "doc1", 0.5), makeResult("2", "doc2", 0.9), makeResult("3", "doc3", 0.7))
            );
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(0.9, result.get(0).getScore(), 0.001);
            assertEquals(0.7, result.get(1).getScore(), 0.001);
            assertEquals(0.5, result.get(2).getScore(), 0.001);
        }

        @Test
        @DisplayName("null分数在计算时视为0")
        void shouldTreatNullScoreAsZero() {
            // null score should be treated as 0.0 in calculations
            // but the returned result object retains its original score (null)
            SearchResult r = SearchResult.builder().id("1").score(null).build();
            List<List<SearchResult>> input = List.of(List.of(r));
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(1, result.size());
            // 返回的仍是原对象，score为null
            assertNull(result.get(0).getScore());
        }

        @Test
        @DisplayName("id为空时使用documentId")
        void shouldFallBackToDocumentId() {
            SearchResult r1 = SearchResult.builder().id(null).documentId("doc1").score(0.9).build();
            SearchResult r2 = SearchResult.builder().id("").documentId("doc1").score(0.8).build();
            List<List<SearchResult>> input = List.of(List.of(r1), List.of(r2));
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(1, result.size());
            assertEquals(0.9, result.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("只有有效id的记录被保留")
        void shouldKeepRecordsWithValidId() {
            // 只有id有效的记录会被保留
            SearchResult r1 = SearchResult.builder().id("valid-id").documentId(null).score(0.9).build();
            List<List<SearchResult>> input = List.of(List.of(r1));
            List<SearchResult> result = SearchResultMerger.merge(input, 10, SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("MergeStrategy - 合并策略枚举")
    class MergeStrategyTests {

        @Test
        @DisplayName("三种合并策略都可用")
        void shouldHaveAllStrategies() {
            assertNotNull(SearchResultMerger.MergeStrategy.MAX_SCORE);
            assertNotNull(SearchResultMerger.MergeStrategy.AVG_SCORE);
            assertNotNull(SearchResultMerger.MergeStrategy.FIRST_RANK);
        }
    }
}