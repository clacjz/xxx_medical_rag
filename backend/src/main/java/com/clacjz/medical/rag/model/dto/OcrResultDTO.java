package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.OcrStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResultDTO {

    private String documentId;
    private Integer totalPages;
    private Integer currentPage;
    private String ocrText;
    private String htmlTable;
    private OcrStatus status;
    private String errorMessage;
}