package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多 query 检索结果合并工具。
 * 支持将 N 组检索结果（来自同一检索策略）按 chunk ID 去重合并。
 */
public class SearchResultMerger {

    /**
     * 合并策略
     */
    public enum MergeStrategy {
        /** 取同一 chunk 在各结果集中的最高分（推荐，与同策略检索兼容） */
        MAX_SCORE,
        /** 平均各结果集中的分数 */
        AVG_SCORE,
        /** 保留第一个结果集中该 chunk 的分数 */
        FIRST_RANK
    }

    /**
     * 合并多组检索结果。
     *
     * @param resultLists 各 query 的检索结果列表
     * @param topK        最终返回 topK 条
     * @param strategy    合并策略
     * @return 合并后按分数降序排列的结果列表
     */
    public static List<SearchResult> merge(
            List<List<SearchResult>> resultLists,
            int topK,
            MergeStrategy strategy) {

        if (resultLists == null || resultLists.isEmpty()) return Collections.emptyList();

        // 单组结果直接截取返回
        if (resultLists.size() == 1) {
            List<SearchResult> single = resultLists.get(0);
            return single.stream()
                    .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        // 按 id 去重合并（优先使用 chunk id，回退至 documentId）
        Map<String, ResultEntry> merged = new LinkedHashMap<>();

        for (int listIdx = 0; listIdx < resultLists.size(); listIdx++) {
            List<SearchResult> results = resultLists.get(listIdx);
            if (results == null) continue;

            for (SearchResult r : results) {
                String key = r.getId();
                if (key == null || key.isBlank()) {
                    key = r.getDocumentId();
                }
                if (key == null || key.isBlank()) continue;

                double score = r.getScore() != null ? r.getScore() : 0.0;

                ResultEntry existing = merged.get(key);
                if (existing == null) {
                    merged.put(key, new ResultEntry(r, listIdx, 1, score));
                } else {
                    // 更新计数和累计分数
                    existing.count++;
                    existing.scoreSum += score;
                    // MAX_SCORE: 保留高分
                    double existingScore = existing.result.getScore() != null ? existing.result.getScore() : 0.0;
                    if (strategy == MergeStrategy.MAX_SCORE && score > existingScore) {
                        existing.result = r;
                    }
                    // FIRST_RANK: 保留第一个
                    if (strategy == MergeStrategy.FIRST_RANK && listIdx < existing.firstListIdx) {
                        existing.result = r;
                        existing.firstListIdx = listIdx;
                    }
                }
            }
        }

        // 按策略计算最终分数并排序
        return merged.values().stream()
                .map(entry -> {
                    if (strategy == MergeStrategy.AVG_SCORE && entry.count > 1) {
                        double avg = entry.scoreSum / entry.count;
                        // 创建一个分数更新后的副本（返回新对象避免副作用）
                        SearchResult original = entry.result;
                        return SearchResult.builder()
                                .id(original.getId())
                                .documentId(original.getDocumentId())
                                .collectionId(original.getCollectionId())
                                .text(original.getText())
                                .score(avg)
                                .title(original.getTitle())
                                .snippet(original.getSnippet())
                                .metadata(original.getMetadata())
                                .startPage(original.getStartPage())
                                .endPage(original.getEndPage())
                                .build();
                    }
                    return entry.result;
                })
                .sorted(Comparator.comparing(
                        (SearchResult r) -> r.getScore() != null ? r.getScore() : 0.0,
                        Comparator.reverseOrder()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /** 内部辅助记录 */
    private static class ResultEntry {
        SearchResult result;
        int firstListIdx;
        int count;
        double scoreSum;

        ResultEntry(SearchResult result, int firstListIdx, int count, double scoreSum) {
            this.result = result;
            this.firstListIdx = firstListIdx;
            this.count = count;
            this.scoreSum = scoreSum;
        }
    }
}
