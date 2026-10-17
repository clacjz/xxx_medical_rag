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
public class BatchAccessCheckResult {

    private List<DocumentAccessResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentAccessResult {
        private String documentId;
        private boolean hasAccess;
        private String reason;
        private String accessedDeptId;
        private String matchReason;
    }
}
