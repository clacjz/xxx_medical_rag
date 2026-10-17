package com.clacjz.medical.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDocumentDTO {

    private String id;
    private String collectionId;
    private String documentId;
    private String documentName;
    private String documentType;
    private String departmentId;
    private String addedBy;
    private String addedByName;
    private String processingStatus;
    private Integer processingPriority;
    private String processingError;
    private LocalDateTime processedAt;
    private LocalDateTime addedAt;
}
