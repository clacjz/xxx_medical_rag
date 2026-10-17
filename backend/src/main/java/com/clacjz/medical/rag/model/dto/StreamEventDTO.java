package com.clacjz.medical.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEventDTO {
    private String type;      // token, reasoning, source, done, error, rewrite
    private String content;   // 回答内容
    private String reasoning; // 思考过程
    private String rewrittenQuery; // 重写后的query
    private String documentId;
    private String title;
    private String snippet;
    private Double score;
    private String collectionId;
    private Integer startPage;
    private Integer endPage;
    private String message;
    private Integer tokensUsed;
    private Long generationTimeMs;
    private String messageId;
    private String queryType;
    private List<String> rewrittenQueries; // 所有改写后的 query 变体
    private Boolean isDecomposed;          // 是否经过子查询分解
    private Integer queryCount;            // 实际使用的检索 query 数量
}
