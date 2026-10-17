package com.clacjz.medical.rag.model.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrRequest {
    private String documentId;
    private String filePath;
    private Integer page;
    private String model;
}
