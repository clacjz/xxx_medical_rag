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
public class ProcessStatusDTO {

    private String documentId;
    private String uploadStatus;
    private String parseStatus;
    private String chunkStatus;
    private String vectorStatus;
    private Integer progress;
    private String currentStep;
    private LocalDateTime startTime;
    private LocalDateTime estimatedEndTime;
}
