package com.clacjz.medical.rag.model.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch OCR 单页请求项。
 * POST /api/glm/batch-ocr 的请求体为 List<BatchOcrItem>（JSON 数组）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchOcrItem {
    /** 从 0 开始的区域索引 */
    private int index;

    /** base64 编码的 JPEG 图片数据 */
    private String imageData;

    /** 任务类型：text / table，默认 text */
    @Builder.Default
    private String taskType = "text";
}
