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
public class QueryResultDTO {

    private String answer;
    private String reasoning;
    private List<SourceDTO> sources;
    private Boolean usedExpansion;
    private Integer tokensUsed;
    private Integer promptTokens;
    private Long generationTimeMs;
    private String messageId;
    private String queryType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDTO {
        private String documentId;
        private String title;
        private String snippet;
        private Double score;
        private String collectionId;
        private Integer startPage;
        private Integer endPage;
    }
}
