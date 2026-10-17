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
public class BatchInsertResult {

    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<String> failedPointIds;
    private long durationMs;
}
