package com.clacjz.medical.rag.util;

import java.util.HashSet;
import java.util.Set;

/**
 * 字符串相似度计算工具类
 */
public final class SimilarityUtil {

    private SimilarityUtil() {
    }

    /**
     * 计算两个字符串的字符级Jaccard相似度
     * Jaccard = |A ∩ B| / |A ∪ B|
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度值 [0, 1]
     */
    public static double jaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        Set<Character> set1 = new HashSet<>();
        for (char c : s1.toCharArray()) {
            set1.add(c);
        }

        Set<Character> set2 = new HashSet<>();
        for (char c : s2.toCharArray()) {
            set2.add(c);
        }

        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 判断两个字符串是否相似（基于Jaccard相似度阈值）
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @param threshold 相似度阈值 [0, 1]
     * @return 是否相似
     */
    public static boolean isSimilar(String s1, String s2, double threshold) {
        return jaccardSimilarity(s1, s2) >= threshold;
    }
}