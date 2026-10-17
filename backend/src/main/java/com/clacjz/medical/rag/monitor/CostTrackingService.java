package com.clacjz.medical.rag.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 成本追踪服务
 *
 * 设计依据: HLD Section 3.3.4 成本追踪模型
 *
 * 功能:
 * - 计算GPU成本 (单次推理成本 = GPU时间 × GPU单价)
 * - 计算Token成本
 * - 计算存储成本
 * - 生成成本报表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostTrackingService {

    // 默认GPU单价 (元/秒) - 基于V100估算
    private static final double DEFAULT_GPU_UNIT_PRICE = 0.0001;

    // 默认Token单价 (元/token)
    private static final double DEFAULT_TOKEN_UNIT_PRICE = 0.00001;

    // 默认存储单价 (元/月/GB)
    private static final double DEFAULT_STORAGE_UNIT_PRICE = 0.1;

    private final Map<String, CostRecord> costRecords = new ConcurrentHashMap<>();
    private final AtomicReference<Double> totalCostAccumulator = new AtomicReference<>(0.0);

    /**
     * 记录AI查询成本
     */
    public CostRecord recordQueryCost(String traceId, String sessionId, String userId,
                                       long gpuTimeMs, int promptTokens, int completionTokens) {
        double gpuCost = calculateGpuCost(gpuTimeMs, DEFAULT_GPU_UNIT_PRICE);
        double tokenCost = calculateTokenCost(promptTokens, completionTokens, DEFAULT_TOKEN_UNIT_PRICE);
        double totalCost = gpuCost + tokenCost;

        CostRecord record = CostRecord.builder()
                .id(UUID.randomUUID().toString())
                .traceId(traceId)
                .sessionId(sessionId)
                .userId(userId)
                .gpuCost(gpuCost)
                .gpuTimeMs(gpuTimeMs)
                .gpuUnitPrice(DEFAULT_GPU_UNIT_PRICE)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .tokenUnitPrice(DEFAULT_TOKEN_UNIT_PRICE)
                .totalCost(totalCost)
                .createdAt(LocalDateTime.now())
                .build();

        costRecords.put(record.getId(), record);
        totalCostAccumulator.updateAndGet(v -> v + totalCost);

        log.info("[CostTracking] Recorded query cost: traceId={}, totalCost={}", traceId, totalCost);
        return record;
    }

    /**
     * 记录文档处理成本
     */
    public CostRecord recordDocumentProcessingCost(String traceId, String documentId,
                                                    long gpuTimeMs, long storedBytes) {
        double gpuCost = calculateGpuCost(gpuTimeMs, DEFAULT_GPU_UNIT_PRICE);
        double storageCost = calculateStorageCost(storedBytes, 30, DEFAULT_STORAGE_UNIT_PRICE); // 默认保留30天
        double totalCost = gpuCost + storageCost;

        CostRecord record = CostRecord.builder()
                .id(UUID.randomUUID().toString())
                .traceId(traceId)
                .sessionId(documentId)
                .gpuCost(gpuCost)
                .gpuTimeMs(gpuTimeMs)
                .gpuUnitPrice(DEFAULT_GPU_UNIT_PRICE)
                .storedBytes(storedBytes)
                .storageUnitPrice(DEFAULT_STORAGE_UNIT_PRICE)
                .storageDays(30)
                .totalCost(totalCost)
                .createdAt(LocalDateTime.now())
                .build();

        costRecords.put(record.getId(), record);
        totalCostAccumulator.updateAndGet(v -> v + totalCost);

        log.info("[CostTracking] Recorded document processing cost: documentId={}, totalCost={}",
                documentId, totalCost);
        return record;
    }

    /**
     * 记录Embedding成本
     */
    public CostRecord recordEmbeddingCost(String traceId, String documentId,
                                           long gpuTimeMs, int tokens) {
        double gpuCost = calculateGpuCost(gpuTimeMs, DEFAULT_GPU_UNIT_PRICE);
        double tokenCost = calculateTokenCost(tokens, 0, DEFAULT_TOKEN_UNIT_PRICE);
        double totalCost = gpuCost + tokenCost;

        CostRecord record = CostRecord.builder()
                .id(UUID.randomUUID().toString())
                .traceId(traceId)
                .sessionId(documentId)
                .gpuCost(gpuCost)
                .gpuTimeMs(gpuTimeMs)
                .gpuUnitPrice(DEFAULT_GPU_UNIT_PRICE)
                .promptTokens(tokens)
                .tokenUnitPrice(DEFAULT_TOKEN_UNIT_PRICE)
                .totalCost(totalCost)
                .createdAt(LocalDateTime.now())
                .build();

        costRecords.put(record.getId(), record);
        totalCostAccumulator.updateAndGet(v -> v + totalCost);

        log.info("[CostTracking] Recorded embedding cost: documentId={}, totalCost={}", documentId, totalCost);
        return record;
    }

    /**
     * 计算GPU成本
     */
    private double calculateGpuCost(long gpuTimeMs, double gpuUnitPrice) {
        return (gpuTimeMs / 1000.0) * gpuUnitPrice;
    }

    /**
     * 计算Token成本
     */
    private double calculateTokenCost(int promptTokens, int completionTokens, double tokenUnitPrice) {
        return (promptTokens + completionTokens) * tokenUnitPrice;
    }

    /**
     * 计算存储成本
     */
    private double calculateStorageCost(long storedBytes, int days, double storageUnitPrice) {
        double gb = storedBytes / (1024.0 * 1024.0 * 1024.0);
        double months = days / 30.0;
        return gb * months * storageUnitPrice;
    }

    /**
     * 获取成本报表
     */
    public CostReport getCostReport(LocalDateTime startTime, LocalDateTime endTime) {
        List<CostRecord> records = new ArrayList<>(costRecords.values());

        // 按时间过滤
        records.removeIf(r -> r.getCreatedAt().isBefore(startTime) || r.getCreatedAt().isAfter(endTime));

        double totalGpuCost = records.stream().mapToDouble(CostRecord::getGpuCost).sum();
        double totalTokenCost = records.stream()
                .mapToDouble(r -> (r.getPromptTokens() + r.getCompletionTokens()) * r.getTokenUnitPrice())
                .sum();
        double totalStorageCost = records.stream()
                .mapToDouble(r -> {
                    if (r.getStoredBytes() == null) return 0;
                    return calculateStorageCost(r.getStoredBytes(), r.getStorageDays(), r.getStorageUnitPrice());
                })
                .sum();

        long totalGpuTimeMs = records.stream().mapToLong(CostRecord::getGpuTimeMs).sum();
        int totalPromptTokens = records.stream().mapToInt(CostRecord::getPromptTokens).sum();
        int totalCompletionTokens = records.stream().mapToInt(CostRecord::getCompletionTokens).sum();
        long totalStoredBytes = records.stream()
                .mapToLong(r -> r.getStoredBytes() != null ? r.getStoredBytes() : 0)
                .sum();

        int queryCount = (int) records.stream().filter(r -> r.getGpuTimeMs() < 10000).count(); // 假设查询耗时<10s
        int docProcessingCount = records.size() - queryCount;

        return CostReport.builder()
                .startTime(startTime)
                .endTime(endTime)
                .totalCost(totalGpuCost + totalTokenCost + totalStorageCost)
                .gpuCost(totalGpuCost)
                .tokenCost(totalTokenCost)
                .storageCost(totalStorageCost)
                .totalGpuTimeMs(totalGpuTimeMs)
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalStoredBytes(totalStoredBytes)
                .queryCount(queryCount)
                .documentProcessingCount(docProcessingCount)
                .recordCount(records.size())
                .build();
    }

    /**
     * 获取单次查询成本和质量调整后成本
     */
    public CostMetrics getCostMetrics() {
        long totalQueries = costRecords.values().stream()
                .filter(r -> r.getGpuTimeMs() < 10000)
                .count();
        double totalCost = totalCostAccumulator.get();

        double avgCostPerQuery = totalQueries > 0 ? totalCost / totalQueries : 0;

        return CostMetrics.builder()
                .totalCost(totalCost)
                .avgCostPerQuery(avgCostPerQuery)
                .totalQueries(totalQueries)
                .build();
    }

    /**
     * 每日清理过期记录（保留90天）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int before = costRecords.size();

        costRecords.entrySet().removeIf(entry -> entry.getValue().getCreatedAt().isBefore(cutoff));

        log.info("[CostTracking] Cleaned up {} expired cost records (before={}, after={})",
                before - costRecords.size(), before, costRecords.size());
    }

    @lombok.Data
    @lombok.Builder
    public static class CostReport {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double totalCost;
        private double gpuCost;
        private double tokenCost;
        private double storageCost;
        private long totalGpuTimeMs;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private long totalStoredBytes;
        private int queryCount;
        private int documentProcessingCount;
        private int recordCount;
    }

    @lombok.Data
    @lombok.Builder
    public static class CostMetrics {
        private double totalCost;
        private double avgCostPerQuery;
        private long totalQueries;
    }
}