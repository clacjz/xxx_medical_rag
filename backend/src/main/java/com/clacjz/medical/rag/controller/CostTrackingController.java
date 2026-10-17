package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.monitor.CostRecord;
import com.clacjz.medical.rag.monitor.CostTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 成本追踪API
 *
 * 提供:
 * - 成本报表查询
 * - 成本指标查询
 * - 单次查询成本追踪
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/costs")
@RequiredArgsConstructor
@Tag(name = "Cost Tracking API", description = "成本追踪相关接口")
public class CostTrackingController {

    private final CostTrackingService costTrackingService;

    @GetMapping("/report")
    @Operation(summary = "获取成本报表", description = "获取指定时间范围内的成本报表")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<CostTrackingService.CostReport>> getCostReport(
            @RequestParam Long startTime,
            @RequestParam Long endTime) {

        LocalDateTime startDateTime = LocalDateTime.ofEpochSecond(startTime / 1000, 0, ZoneOffset.ofHours(8));
        LocalDateTime endDateTime = LocalDateTime.ofEpochSecond(endTime / 1000, 0, ZoneOffset.ofHours(8));

        log.info("Get cost report: startTime={}, endTime={}", startDateTime, endDateTime);

        CostTrackingService.CostReport report = costTrackingService.getCostReport(startDateTime, endDateTime);
        return ResponseEntity.ok(Result.success(report));
    }

    @GetMapping("/metrics")
    @Operation(summary = "获取成本指标", description = "获取当前成本指标汇总")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<CostTrackingService.CostMetrics>> getCostMetrics() {
        log.info("Get cost metrics");
        return ResponseEntity.ok(Result.success(costTrackingService.getCostMetrics()));
    }

    @PostMapping("/record/query")
    @Operation(summary = "记录查询成本", description = "记录一次AI查询的成本")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CostRecord>> recordQueryCost(
            @RequestParam String traceId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam long gpuTimeMs,
            @RequestParam int promptTokens,
            @RequestParam int completionTokens) {

        log.info("Record query cost: traceId={}, gpuTimeMs={}, tokens={}+{}",
                traceId, gpuTimeMs, promptTokens, completionTokens);

        CostRecord record = costTrackingService.recordQueryCost(
                traceId, sessionId, userId, gpuTimeMs, promptTokens, completionTokens);
        return ResponseEntity.ok(Result.success(record));
    }

    @PostMapping("/record/document")
    @Operation(summary = "记录文档处理成本", description = "记录文档处理的成本")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CostRecord>> recordDocumentCost(
            @RequestParam String traceId,
            @RequestParam String documentId,
            @RequestParam long gpuTimeMs,
            @RequestParam long storedBytes) {

        log.info("Record document processing cost: documentId={}, gpuTimeMs={}, storedBytes={}",
                documentId, gpuTimeMs, storedBytes);

        CostRecord record = costTrackingService.recordDocumentProcessingCost(
                traceId, documentId, gpuTimeMs, storedBytes);
        return ResponseEntity.ok(Result.success(record));
    }

    @GetMapping("/summary")
    @Operation(summary = "获取成本汇总", description = "获取今日/本周/本月的成本汇总")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Map<String, Object>>> getCostSummary(
            @RequestParam(defaultValue = "day") String period) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime;

        switch (period.toLowerCase()) {
            case "week" -> startTime = now.minusWeeks(1);
            case "month" -> startTime = now.minusMonths(1);
            default -> startTime = now.toLocalDate().atStartOfDay();
        }

        CostTrackingService.CostReport report = costTrackingService.getCostReport(startTime, now);

        Map<String, Object> summary = Map.of(
                "period", period,
                "startTime", startTime,
                "endTime", now,
                "totalCost", report.getTotalCost(),
                "gpuCost", report.getGpuCost(),
                "tokenCost", report.getTokenCost(),
                "storageCost", report.getStorageCost(),
                "queryCount", report.getQueryCount(),
                "documentProcessingCount", report.getDocumentProcessingCount()
        );

        return ResponseEntity.ok(Result.success(summary));
    }
}