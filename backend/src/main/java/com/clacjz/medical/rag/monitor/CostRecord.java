package com.clacjz.medical.rag.monitor;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成本追踪数据模型
 *
 * 设计依据: HLD Section 3.3.4 成本追踪模型
 */
@Data
@Builder
public class CostRecord {

    private String id;
    private String traceId;
    private String sessionId;
    private String userId;

    // GPU成本
    private Double gpuCost;           // GPU推理成本
    private Long gpuTimeMs;           // GPU耗时(毫秒)
    private Double gpuUnitPrice;     // GPU单价(元/秒)

    // Token成本
    private Integer promptTokens;
    private Integer completionTokens;
    private Double tokenUnitPrice;   // Token单价

    // 存储成本
    private Long storedBytes;        // 存储字节数
    private Double storageUnitPrice; // 存储单价(元/月/GB)
    private Integer storageDays;     // 存储天数

    // 总成本
    private Double totalCost;

    // 时间戳
    private LocalDateTime createdAt;

    public static final String TYPE_AI_QUERY = "AI_QUERY";
    public static final String TYPE_DOCUMENT_PROCESSING = "DOCUMENT_PROCESSING";
    public static final String TYPE_EMBEDDING = "EMBEDDING";
}