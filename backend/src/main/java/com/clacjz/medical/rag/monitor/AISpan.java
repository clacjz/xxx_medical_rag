package com.clacjz.medical.rag.monitor;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * AI链路追踪Span数据模型
 *
 * 设计依据: HLD Section 3.4.3 AI Trace索引结构
 */
@Data
@Builder
public class AISpan {

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String operationName;

    // Span类型: embedding_query, vector_search, rerank, prompt_construction, llm_generate, answer_verification
    private String spanType;

    // 时间戳
    private Instant startTime;
    private Instant endTime;
    private long durationMs;

    // 输入输出信息
    private String inputText;
    private String outputText;
    private Map<String, Object> metadata;

    // 关联实体
    private String userId;
    private String sessionId;
    private String collectionId;

    // 质量指标
    private Double similarityScore;
    private Integer tokenCount;
    private Integer chunkCount;

    public static final String TYPE_EMBEDDING_QUERY = "embedding_query";
    public static final String TYPE_VECTOR_SEARCH = "vector_search";
    public static final String TYPE_RERANK = "rerank";
    public static final String TYPE_PROMPT_CONSTRUCTION = "prompt_construction";
    public static final String TYPE_LLM_GENERATE = "llm_generate";
    public static final String TYPE_ANSWER_VERIFICATION = "answer_verification";
}