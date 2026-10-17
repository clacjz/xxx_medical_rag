package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatQueryRequest {

    private String sessionId;

    @NotBlank(message = "查询内容不能为空")
    private String query;

    @NotEmpty(message = "至少需要选择一个Collection")
    private List<String> collectionIds;

    @Builder.Default
    private Boolean noExpansion = false;

    private String userId;

    @Builder.Default
    private Integer topK = 50;

    private Double similarityThreshold;

    // LLM 控制参数
    @Builder.Default
    private Double temperature = 0.7;

    @Builder.Default
    private Double topP = 0.9;

    @Builder.Default
    private Integer maxTokens = 2000;

    @Builder.Default
    private Double repetitionPenalty = 1.1;

    @Builder.Default
    private Double presencePenalty = 0.0;

    @Builder.Default
    private Boolean thinkingEnabled = false;

    /** 思考过程最大token预算，null表示使用服务端默认值（4096） */
    @Builder.Default
    private Integer thinkingMaxTokens = null;

    // 是否启用会话历史
    @Builder.Default
    private Boolean enableHistory = false;

    // 会话历史（当enableHistory=true时传递）
    private List<Map<String, String>> history;
}
