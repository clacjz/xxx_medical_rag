package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 医学术语归一化服务
 * 从 medical-term-mappings.json 加载同义词组映射表，
 * 检测 query 是否命中组内任意词，若是则在 query 末尾追加整组。
 * 纯字符串操作，零延迟，用于缩小中医/西医术语鸿沟。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTermNormalizer {

    private final ObjectMapper objectMapper;

    /** 每组同义词，如 ["适应症", "功能主治", "主治"] */
    private volatile List<List<String>> synonymGroups = List.of();

    /** 所有词的快速查找索引 → 所属组 */
    private volatile Map<String, List<String>> termIndex = Map.of();

    /** 预编译词边界模式，用于精确匹配 */
    private volatile Map<String, Pattern> termPatterns = Map.of();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("medical-term-mappings.json");
            if (!resource.exists()) {
                log.warn("[MedicalTermNormalizer] medical-term-mappings.json not found, skipping");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                List<String> rawGroups = objectMapper.readValue(is, new TypeReference<>() {});
                List<List<String>> groups = new ArrayList<>();
                Map<String, List<String>> index = new HashMap<>();
                Map<String, Pattern> patterns = new HashMap<>();

                for (String raw : rawGroups) {
                    if (raw == null || raw.isBlank()) continue;
                    String[] parts = raw.trim().split("\\s+");
                    if (parts.length < 2) continue;
                    List<String> group = Collections.unmodifiableList(Arrays.asList(parts));
                    groups.add(group);
                    for (String term : group) {
                        if (index.containsKey(term)) {
                            log.warn("[MedicalTermNormalizer] Term '{}' appears in multiple groups, using last occurrence", term);
                        }
                        index.put(term, group);
                        // 改用仅 \w（字母/数字/下划线）边界，去掉 CJK 字符边界：
                        // 原版 (?<![一-鿿\w]) 会阻止"功能主治"中的"主治"被匹配（前有"能"），
                        // 导致同义词组"主治→适应症"无法扩展。去掉 CJK 负向后顾后，只要不是字母/数字
                        // 包围即可匹配，对于纯 CJK 文本这更符合预期（同义词应当触发而非被块内字符挡住）。
                        patterns.put(term, Pattern.compile(
                                "(?<!\\w)" + Pattern.quote(term) + "(?!\\w)"
                        ));
                    }
                }

                this.synonymGroups = Collections.unmodifiableList(groups);
                this.termIndex = index;
                this.termPatterns = patterns;
                log.info("[MedicalTermNormalizer] Loaded {} synonym groups ({} terms)", groups.size(), index.size());
            }
        } catch (IOException e) {
            log.error("[MedicalTermNormalizer] Failed to load medical-term-mappings.json", e);
        }
    }

    /**
     * 归一化 query 中的医学术语
     * 检测 query 是否命中任一组中的词 → 若是，在 query 末尾追加整组（去重）
     * 使用 CJK 词边界匹配，避免"主治医师"误匹配"主治"
     */
    public String normalize(String query) {
        if (query == null || query.isBlank() || termIndex.isEmpty()) {
            return query;
        }

        Set<String> matchedTerms = new HashSet<>();
        for (Map.Entry<String, Pattern> entry : termPatterns.entrySet()) {
            if (entry.getValue().matcher(query).find()) {
                List<String> group = termIndex.get(entry.getKey());
                if (group != null) {
                    matchedTerms.addAll(group);
                }
            }
        }

        if (matchedTerms.isEmpty()) {
            return query;
        }

        // 追加未出现在原 query 中的同义词
        StringBuilder sb = new StringBuilder(query);
        for (String synonym : matchedTerms) {
            // 检查累积结果而非原始 query，确保幂等性
            if (!sb.toString().contains(synonym)) {
                sb.append(" ").append(synonym);
            }
        }

        String result = sb.toString();
        if (!result.equals(query)) {
            log.debug("[MedicalTermNormalizer] '{}' → '{}'", query, result);
        }
        return result;
    }

    /** 获取已加载的同义词组（供 ES 索引配置等模块使用） */
    public List<List<String>> getSynonymGroups() {
        return synonymGroups;
    }
}
