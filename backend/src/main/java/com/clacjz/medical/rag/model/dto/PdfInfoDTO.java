package com.clacjz.medical.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfInfoDTO {

    private String documentId;
    private String filePath;
    private Integer totalPages;
    private String fileType;
}