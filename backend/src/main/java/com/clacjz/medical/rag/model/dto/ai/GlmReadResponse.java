package com.clacjz.medical.rag.model.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlmReadResponse {
    private java.util.List<FileResult> results;
    private ProcessSummary summary;
    private String request_id;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileResult {
        private String filename;
        private String status;
        private String content;
        private String extracted;
        private Object data;
        private String error;
        private Integer size;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProcessSummary {
        private Integer total;
        private Integer success;
        private Integer failed;
        private Long total_size;
    }
}
