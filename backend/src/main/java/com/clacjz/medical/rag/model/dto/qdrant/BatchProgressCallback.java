package com.clacjz.medical.rag.model.dto.qdrant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProgressCallback {

    private String batchId;
    private int currentBatch;
    private int totalBatches;
    private int processedItems;
    private int totalItems;
    private long startTimeMs;
    private Double progressPercent;
    private Long estimatedRemainingMs;
}
