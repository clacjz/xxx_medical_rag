package com.clacjz.medical.rag.model.dto.qdrant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeState {

    private String batchId;
    private String collectionName;
    private int currentBatchIndex;
    private int totalBatches;
    private int processedItems;
    private int totalItems;
    private Status status;
    private long startTimeMs;
    private long lastUpdatedMs;
    private List<String> processedPointIds;
    private String errorMessage;

    public enum Status {
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
