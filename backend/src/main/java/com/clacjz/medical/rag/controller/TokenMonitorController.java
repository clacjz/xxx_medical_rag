package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/token-monitor")
@RequiredArgsConstructor
@Tag(name = "Token Monitor API", description = "Token黑名单监控接口")
public class TokenMonitorController {

    private final TokenBlacklistService tokenBlacklistService;

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "获取Token黑名单统计", description = "获取黑名单检查统计信息")
    public ResponseEntity<Result<Map<String, Object>>> getStatistics() {
        Map<String, Object> stats = tokenBlacklistService.getStatistics();
        return ResponseEntity.ok(Result.success("获取成功", stats));
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "Redis健康检查", description = "检查Redis连接状态")
    public ResponseEntity<Result<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = tokenBlacklistService.healthCheck();
        return ResponseEntity.ok(Result.success("健康检查完成", health));
    }

    @PostMapping("/statistics/reset")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "重置统计信息", description = "重置Token黑名单统计计数")
    public ResponseEntity<Result<Void>> resetStatistics() {
        tokenBlacklistService.resetStatistics();
        return ResponseEntity.ok(Result.success("统计信息已重置", null));
    }
}
