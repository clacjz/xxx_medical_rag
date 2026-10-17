package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName(value = "chat_messages", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("session_id")
    private String sessionId;

    @TableField(exist = false)
    private ChatSession session;

    @TableField("department_id")
    private String departmentId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("reasoning")
    private String reasoning;

    @TableField(value = "sources", typeHandler = com.clacjz.medical.rag.mapper.JsonbTypeHandler.class)
    private String sourcesJson;

    @TableField(value = "query_params", typeHandler = com.clacjz.medical.rag.mapper.JsonbTypeHandler.class)
    private String queryParamsJson;

    @TableField(value = "model_info", typeHandler = com.clacjz.medical.rag.mapper.JsonbTypeHandler.class)
    private String modelInfoJson;

    @TableField(value = "token_usage", typeHandler = com.clacjz.medical.rag.mapper.JsonbTypeHandler.class)
    private String tokenUsageJson;

    @TableField("generation_time_ms")
    private Integer generationTimeMs;

    @TableField("feedback")
    private String feedback;

    @TableField("feedback_comment")
    private String feedbackComment;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // 瞬时字段
    @TableField(exist = false)
    @Builder.Default
    private List<SourceReference> sources = new ArrayList<>();

    @TableField(exist = false)
    private QueryParams queryParams;

    @TableField(exist = false)
    private ModelInfo modelInfo;

    @TableField(exist = false)
    private TokenUsage tokenUsage;

    public List<SourceReference> getSources() {
        if (sources == null && sourcesJson != null && !sourcesJson.isBlank()) {
            try {
                sources = objectMapper.readValue(sourcesJson, new TypeReference<List<SourceReference>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse sources JSON", e);
            }
        }
        if (sources == null) {
            sources = new ArrayList<>();
        }
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources;
        if (sources != null) {
            try {
                this.sourcesJson = objectMapper.writeValueAsString(sources);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize sources to JSON", e);
            }
        }
    }

    public QueryParams getQueryParams() {
        if (queryParams == null && queryParamsJson != null && !queryParamsJson.isBlank()) {
            try {
                queryParams = objectMapper.readValue(queryParamsJson, QueryParams.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse queryParams JSON", e);
            }
        }
        return queryParams;
    }

    public void setQueryParams(QueryParams queryParams) {
        this.queryParams = queryParams;
        if (queryParams != null) {
            try {
                this.queryParamsJson = objectMapper.writeValueAsString(queryParams);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize queryParams to JSON", e);
            }
        }
    }

    public ModelInfo getModelInfo() {
        if (modelInfo == null && modelInfoJson != null && !modelInfoJson.isBlank()) {
            try {
                modelInfo = objectMapper.readValue(modelInfoJson, ModelInfo.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse modelInfo JSON", e);
            }
        }
        return modelInfo;
    }

    public void setModelInfo(ModelInfo modelInfo) {
        this.modelInfo = modelInfo;
        if (modelInfo != null) {
            try {
                this.modelInfoJson = objectMapper.writeValueAsString(modelInfo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize modelInfo to JSON", e);
            }
        }
    }

    public TokenUsage getTokenUsage() {
        if (tokenUsage == null && tokenUsageJson != null && !tokenUsageJson.isBlank()) {
            try {
                tokenUsage = objectMapper.readValue(tokenUsageJson, TokenUsage.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tokenUsage JSON", e);
            }
        }
        return tokenUsage;
    }

    public void setTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
        if (tokenUsage != null) {
            try {
                this.tokenUsageJson = objectMapper.writeValueAsString(tokenUsage);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize tokenUsage to JSON", e);
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReference implements java.io.Serializable {
        private String documentId;
        private String title;
        private String snippet;
        private Double score;
        private String collectionId;
        private Integer startPage;
        private Integer endPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryParams implements java.io.Serializable {
        private List<String> collectionIds;
        private Boolean noExpansion;
        private Integer topK;
        private Double similarityThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo implements java.io.Serializable {
        private String modelName;
        private String embeddingModel;
        private Double temperature;
        private Integer maxTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage implements java.io.Serializable {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    public boolean isUserMessage() {
        return "user".equalsIgnoreCase(role);
    }

    public boolean isAssistantMessage() {
        return "assistant".equalsIgnoreCase(role);
    }
}