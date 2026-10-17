package com.clacjz.medical.rag.model.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch OCR 完整响应体。
 * 对应 POST /api/glm/batch-ocr 的响应 JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchOcrResponse {
    private java.util.List<BatchItemResult> results;
    private BatchStats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchItemResult {
        /** 对应请求中的 index */
        private int index;

        /** 识别文本内容 */
        private String content;

        /** 状态：success / failed */
        private String status;

        /** 错误信息（仅 failed 时有值） */
        private String error;

        /** 页码（部分AI SDK版本可能返回） */
        private Integer pageNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchStats {
        /** 总区域数 */
        private Integer totalRegions;

        /** 实际发出的 batch 请求数 */
        private Integer batchCount;

        /** 退化到单次识别的数量 */
        private Integer fallbackCount;

        /** 成功数量 */
        private Integer successCount;

        /** 失败数量 */
        private Integer failedCount;
    }
}
