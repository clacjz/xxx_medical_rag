package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AITracingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI链路可观测性API
 *
 * 提供:
 * - AI质量指标查询
 * - 追踪数据查询
 * - 质量告警状态
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-observability")
@RequiredArgsConstructor
@Tag(name = "AI Observability API", description = "AI链路可观测性相关接口")
public class AIObservabilityController {

    private final AIMetricsService metricsService;
    private final AITracingService tracingService;

    @GetMapping("/metrics")
    @Operation(summary = "获取AI质量指标", description = "获取当前AI链路质量指标快照")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<AIMetricsService.MetricsSnapshot>> getMetrics() {
        log.info("Get AI metrics snapshot");
        return ResponseEntity.ok(Result.success(metricsService.getSnapshot()));
    }

    @GetMapping("/metrics/retrieval")
    @Operation(summary = "获取检索质量指标", description = "获取检索质量详细指标")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Map<String, Object>>> getRetrievalMetrics() {
        AIMetricsService.MetricsSnapshot snapshot = metricsService.getSnapshot();

        Map<String, Object> metrics = Map.of(
                "recallAt5", snapshot.getRetrievalRecall(),
                "precisionAt5", snapshot.getRetrievalPrecision(),
                "mrr", snapshot.getRetrievalMrr(),
                "ndcgAt5", snapshot.getRetrievalNdcg(),
                "target", Map.of(
                        "recallAt5", 0.75,
                        "precisionAt5", 0.60,
                        "mrr", 0.70,
                        "ndcgAt5", 0.65
                ),
                "status", metricsService.isRetrievalQualityAcceptable(
                        AITracingService.RetrievalMetrics.builder()
                                .recallAtK(snapshot.getRetrievalRecall())
                                .precisionAtK(snapshot.getRetrievalPrecision())
                                .mrr(snapshot.getRetrievalMrr())
                                .ndcgAtK(snapshot.getRetrievalNdcg())
                                .build()
                ) ? "OK" : "DEGRADED"
        );

        return ResponseEntity.ok(Result.success(metrics));
    }

    @GetMapping("/metrics/generation")
    @Operation(summary = "获取生成质量指标", description = "获取生成质量详细指标")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Map<String, Object>>> getGenerationMetrics() {
        AIMetricsService.MetricsSnapshot snapshot = metricsService.getSnapshot();

        Map<String, Object> metrics = Map.of(
                "qualityScore", snapshot.getGenerationQualityScore(),
                "hallucinationRate", snapshot.getHallucinationRate(),
                "citationAccuracy", snapshot.getCitationAccuracy(),
                "target", Map.of(
                        "qualityScore", 80,
                        "hallucinationRate", 0.05,
                        "citationAccuracy", 0.90
                ),
                "status", metricsService.isGenerationQualityAcceptable(
                        AITracingService.GenerationMetrics.builder()
                                .qualityScore(snapshot.getGenerationQualityScore())
                                .hallucinationRate(snapshot.getHallucinationRate())
                                .citationAccuracy(snapshot.getCitationAccuracy())
                                .build()
                ) ? "OK" : "DEGRADED"
        );

        return ResponseEntity.ok(Result.success(metrics));
    }

    @GetMapping("/tracing/coverage")
    @Operation(summary = "获取追踪覆盖率", description = "获取AI链路追踪覆盖率")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Map<String, Object>>> getTracingCoverage() {
        double coverage = tracingService.getTracingCoverage();

        Map<String, Object> result = Map.of(
                "coverage", coverage,
                "target", 95.0,
                "status", coverage >= 95 ? "OK" : "INSUFFICIENT"
        );

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/alerts")
    @Operation(summary = "获取AI质量告警", description = "获取当前AI质量告警状态")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<Map<String, Object>>>> getAlerts() {
        AIMetricsService.MetricsSnapshot snapshot = metricsService.getSnapshot();
        List<Map<String, Object>> alerts = new java.util.ArrayList<>();

        // 检查检索质量
        if (snapshot.getRetrievalRecall() < 0.75) {
            alerts.add(Map.of(
                    "type", "RETRIEVAL_QUALITY",
                    "severity", "WARNING",
                    "message", "检索Recall@5低于目标值(0.75)",
                    "current", snapshot.getRetrievalRecall()
            ));
        }

        // 检查生成质量
        if (snapshot.getGenerationQualityScore() < 80) {
            alerts.add(Map.of(
                    "type", "GENERATION_QUALITY",
                    "severity", "WARNING",
                    "message", "生成质量评分低于目标值(80)",
                    "current", snapshot.getGenerationQualityScore()
            ));
        }

        // 检查幻觉率
        if (snapshot.getHallucinationRate() > 0.05) {
            alerts.add(Map.of(
                    "type", "HALLUCINATION",
                    "severity", "CRITICAL",
                    "message", "生成幻觉率超过目标值(5%)",
                    "current", snapshot.getHallucinationRate()
            ));
        }

        // 检查追踪覆盖率
        if (snapshot.getTracingCoverage() < 95) {
            alerts.add(Map.of(
                    "type", "TRACING_COVERAGE",
                    "severity", "WARNING",
                    "message", "AI链路追踪覆盖率低于目标值(95%)",
                    "current", snapshot.getTracingCoverage()
            ));
        }

        return ResponseEntity.ok(Result.success(alerts));
    }
}