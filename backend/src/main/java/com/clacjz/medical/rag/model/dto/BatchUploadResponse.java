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
public class BatchUploadResponse {

    private Integer totalFiles;

    private Integer successCount;

    private Integer failedCount;

    private Boolean autoProcess;

    private List<CollectionBatchUploadResult> results;

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionBatchUploadResult {
        private Boolean success;
        private String documentId;
        private String fileName;
        private Long fileSize;
        private Integer chunkCount;
        private Integer charCount;
        private String errorMessage;
    }
}
