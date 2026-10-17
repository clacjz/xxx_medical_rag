package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.dto.ner.NerEntity;
import com.clacjz.medical.rag.model.dto.ner.NerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NER客户端
 * 通过LLM（Qwen）进行医学实体识别
 * 未来可切换为专用NER服务（ai-sdk /api/ner/extract）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NerClient {

    private static final String EXTRACTION_PROMPT =
            "你是一个医学实体识别助手。从用户输入的文本中提取医学相关实体，只返回JSON格式结果。\n\n" +
            "支持的实体类型：\n" +
            "- DISEASE: 疾病名称（如：糖尿病、高血压）\n" +
            "- DRUG: 药品名称（如：阿司匹林、二甲双胍）\n" +
            "- SYMPTOM: 症状（如：头痛、发热、恶心）\n" +
            "- ORG: 机构/厂商（如：拜耳医药、国家药监局）\n" +
            "- REGION: 地域（如：北京、上海）\n\n" +
            "返回格式（严格JSON，不要markdown代码块）：\n" +
            "{\"entities\": [{\"text\": \"实体文本\", \"type\": \"DISEASE\", \"aliases\": []}]}";

    private static final String INDICATIONS_EXTRACTION_PROMPT =
            "你是一个医学实体识别助手。从用户输入的药品适应症文本中提取涉及的疾病和症状。\n\n" +
            "返回格式（严格JSON，不要markdown代码块）：\n" +
            "{\"diseases\": [\"疾病名称\"], \"symptoms\": [\"症状名称\"]}";

    /** 单次NER调用的最大输入字符数（LLM context window 安全边界） */
    private static final int NER_SEGMENT_SIZE = 4000;

    /**
     * 文档级NER提取的最大分段数。超过此数量时截断（防止超长文档打爆 token 预算）。
     * 4000 × 8 = 32000 字符，对 99% 的医疗文档已足够。
     */
    private static final int NER_MAX_SEGMENTS = 8;

    /** 适应症NER输入最大字符数 */
    private static final int NER_MAX_INPUT_LENGTH = 4000;

    private final LLMService llmService;
    private final RAGProperties ragProperties;
    private final ObjectMapper objectMapper;

    /**
     * 从单段文本中提取医学实体（查询场景使用，不会分段）
     */
    public NerResponse extractEntities(String text) {
        if (!ragProperties.getNer().isEnabled() || text == null || text.isBlank()) {
            return new NerResponse(Collections.emptyList());
        }

        // 单段调用直接截断
        String truncatedText = text.length() > NER_SEGMENT_SIZE
                ? text.substring(0, NER_SEGMENT_SIZE) : text;

        return doExtract(truncatedText);
    }

    /**
     * 从文档全文中提取医学实体 — 全量分段提取后合并去重
     *
     * 将文档按自然边界（段落、句子结尾）切分为 NER_SEGMENT_SIZE 大小的分段，
     * 每段独立调用 LLM NER，结果按 {@code text:type} 去重合并。
     * 最多处理 NER_MAX_SEGMENTS 段，超出部分丢弃并记录警告。
     */
    public NerResponse extractEntitiesFromDocument(String fullText) {
        if (!ragProperties.getNer().isEnabled() || fullText == null || fullText.isBlank()) {
            return new NerResponse(Collections.emptyList());
        }

        List<String> segments = splitForNer(fullText);
        if (segments.size() > NER_MAX_SEGMENTS) {
            log.warn("Document too long for full NER, dropping {} segments (total={}, max={})",
                    segments.size() - NER_MAX_SEGMENTS, segments.size(), NER_MAX_SEGMENTS);
            segments = segments.subList(0, NER_MAX_SEGMENTS);
        }

        if (segments.size() > 1) {
            log.info("Document NER: split into {} segments (totalChars={})", segments.size(), fullText.length());
        }

        Set<String> seen = new HashSet<>();
        List<NerEntity> allEntities = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            NerResponse segmentResult = doExtract(segments.get(i));
            if (segmentResult.getEntities() != null) {
                for (NerEntity entity : segmentResult.getEntities()) {
                    if (entity.getText() == null || entity.getText().isBlank()) continue;
                    String key = entity.getText() + "::" + (entity.getType() != null ? entity.getType() : "");
                    if (seen.add(key)) {
                        allEntities.add(entity);
                    }
                }
            }
        }

        log.debug("Document NER completed: {} segments, {} unique entities (before dedup: {})",
                segments.size(), allEntities.size(), seen.size());
        return new NerResponse(allEntities);
    }

    /**
     * 执行一次 LLM NER 调用（内部方法，不含分段逻辑）
     */
    private NerResponse doExtract(String text) {
        try {
            String jsonResponse = llmService.generateJson(EXTRACTION_PROMPT, text);
            NerResponse response = parseExtractionResponse(jsonResponse);
            log.debug("NER extracted {} entities from {} chars",
                    response.getEntities() != null ? response.getEntities().size() : 0, text.length());
            return response;
        } catch (Exception e) {
            log.warn("NER extraction failed, returning empty:", e);
            return new NerResponse(Collections.emptyList());
        }
    }

    /**
     * 将全文按自然边界分段，每段 ≤ NER_SEGMENT_SIZE
     */
    private List<String> splitForNer(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + NER_SEGMENT_SIZE, text.length());
            if (end < text.length()) {
                // 向后查找自然边界（段落 > 句号 > 逗号 > 换行）
                int boundary = findNaturalBoundary(text, end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            segments.add(text.substring(start, end).trim());
            start = end;
            // 跳过连续空白
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return segments;
    }

    /**
     * 在 end 附近查找自然断点：段落后界 > 句号 > 逗号 > 换行
     */
    private int findNaturalBoundary(String text, int end) {
        int searchStart = Math.max(end - 200, 0);
        // 按优先级：先找段落分隔（连续两个换行），再找句号、逗号
        int paraBreak = text.lastIndexOf("\n\n", end - 1);
        if (paraBreak > searchStart) return paraBreak + 2;

        int period = text.lastIndexOf("。", end - 1);
        if (period > searchStart) return period + 1;

        int comma = text.lastIndexOf("，", end - 1);
        if (comma > searchStart) return comma + 1;

        int newline = text.lastIndexOf("\n", end - 1);
        if (newline > searchStart) return newline + 1;

        return -1; // 找不到合适的边界
    }

    /**
     * 从适应症文本中提取疾病和症状
     * 用于MANUAL文档的适应症字段二次提取（超过 NER_MAX_INPUT_LENGTH 时自动截断）
     */
    public IndicationsResult extractIndications(String indicationsText) {
        if (!ragProperties.getNer().isEnabled() || indicationsText == null || indicationsText.isBlank()) {
            return new IndicationsResult(Collections.emptyList(), Collections.emptyList());
        }

        String truncatedText = indicationsText.length() > NER_MAX_INPUT_LENGTH
                ? indicationsText.substring(0, NER_MAX_INPUT_LENGTH) : indicationsText;
        if (truncatedText.length() < indicationsText.length()) {
            log.debug("Indications NER input truncated: original={}, truncated={}",
                    indicationsText.length(), NER_MAX_INPUT_LENGTH);
        }

        try {
            String jsonResponse = llmService.generateJson(INDICATIONS_EXTRACTION_PROMPT, truncatedText);

            IndicationsResult result = parseIndicationsResponse(jsonResponse);
            log.debug("Indications NER: {} diseases, {} symptoms from {} chars",
                    result.diseases().size(), result.symptoms().size(), indicationsText.length());
            return result;
        } catch (Exception e) {
            log.warn("Indications NER failed, returning empty:", e);
            return new IndicationsResult(Collections.emptyList(), Collections.emptyList());
        }
    }

    @SuppressWarnings("unchecked")
    private NerResponse parseExtractionResponse(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> entitiesRaw = (List<Map<String, Object>>) root.get("entities");
            if (entitiesRaw == null || entitiesRaw.isEmpty()) {
                return new NerResponse(Collections.emptyList());
            }

            List<NerEntity> entities = new ArrayList<>();
            for (Map<String, Object> raw : entitiesRaw) {
                // 类型验证，避免 ClassCastException
                Object textObj = raw.getOrDefault("text", "");
                Object typeObj = raw.getOrDefault("type", "");
                Object aliasesObj = raw.getOrDefault("aliases", Collections.emptyList());

                String text = textObj instanceof String ? (String) textObj : textObj.toString();
                String type = typeObj instanceof String ? (String) typeObj : typeObj.toString();
                List<String> aliases = aliasesObj instanceof List ? (List<String>) aliasesObj : Collections.emptyList();

                entities.add(NerEntity.builder()
                        .text(text)
                        .type(type)
                        .aliases(aliases)
                        .build());
            }
            return new NerResponse(entities);
        } catch (Exception e) {
            log.warn("Failed to parse NER response JSON:", e);
            return new NerResponse(Collections.emptyList());
        }
    }

    @SuppressWarnings("unchecked")
    private IndicationsResult parseIndicationsResponse(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            List<String> diseases = (List<String>) root.getOrDefault("diseases", Collections.emptyList());
            List<String> symptoms = (List<String>) root.getOrDefault("symptoms", Collections.emptyList());
            return new IndicationsResult(diseases, symptoms);
        } catch (Exception e) {
            log.warn("Failed to parse indications NER response:", e);
            return new IndicationsResult(Collections.emptyList(), Collections.emptyList());
        }
    }

    public record IndicationsResult(List<String> diseases, List<String> symptoms) {}
}
