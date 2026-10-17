package com.clacjz.medical.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionStatistics {

    private long totalQueries;

    private double avgLatencyMs;

    private double p95LatencyMs;

    private double errorRate;

    private int activeUsers;
}
