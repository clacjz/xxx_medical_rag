package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.dto.AuditLogDTO;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.AuditService;
import com.clacjz.medical.rag.service.ExportService;
import com.clacjz.medical.rag.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Log API", description = "审计日志相关接口")
public class AuditController {

    private final AuditService auditService;
    private final ExportService exportService;

    @GetMapping
    @Operation(summary = "获取审计日志列表", description = "分页查询审计日志（时间范围必填，Unix时间戳秒级）")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<PageResult<AuditLogDTO>>> listAuditLogs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "审计级别") @RequestParam(required = false) AuditLevelEnums level,
            @Parameter(description = "开始时间（必填，Unix时间戳秒级）") @RequestParam Long startTime,
            @Parameter(description = "结束时间（必填，Unix时间戳秒级）") @RequestParam Long endTime) {

        LocalDateTime startDateTime = LocalDateTime.ofEpochSecond(startTime / 1000, 0, ZoneOffset.of("+8"));
        LocalDateTime endDateTime = LocalDateTime.ofEpochSecond(endTime / 1000, 0, ZoneOffset.of("+8"));

        String operator = SecurityContextUtils.getCurrentUsernameOrDefault("unknown");
        log.info("List audit logs: page={}, pageSize={}, userId={}, action={}, level={}, startTime={}, endTime={}, operator={}",
                page, pageSize, userId, action, level, startDateTime, endDateTime, operator);

        IPage<AuditLogDTO> logs = auditService.getAuditLogs(page, pageSize, userId, action, level, startDateTime, endDateTime);

        PageResult<AuditLogDTO> result = PageResult.from(logs, dto -> dto);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取审计日志详情", description = "根据ID获取审计日志详细信息")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<AuditLogDTO>> getAuditLog(
            @Parameter(description = "日志ID") @PathVariable String id) {

        String operator = SecurityContextUtils.getCurrentUsernameOrDefault("unknown");
        log.info("Get audit log: id={}, operator={}", id, operator);

        AuditLogDTO auditLog = auditService.getAuditLogById(id);
        return ResponseEntity.ok(Result.success(auditLog));
    }

    @GetMapping("/export")
    @Operation(summary = "导出审计日志", description = "导出指定时间范围内的审计日志，支持CSV/JSON格式")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> exportAuditLogs(
            @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "导出格式：csv/json") @RequestParam(defaultValue = "csv") String format) {

        String operator = SecurityContextUtils.getCurrentUsernameOrDefault("unknown");
        log.info("Export audit logs: startTime={}, endTime={}, format={}, operator={}",
                startTime, endTime, format, operator);

        String normalizedFormat = format.toLowerCase().trim();
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        if ("json".equals(normalizedFormat)) {
            String jsonContent = exportService.exportAuditLogsToJson(startTime, endTime);
            String filename = "audit_logs_" + timestamp + ".json";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", encodedFilename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(jsonContent);
        } else {
            String csvContent = exportService.exportAuditLogsToCsv(startTime, endTime);
            String filename = "audit_logs_" + timestamp + ".csv";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", encodedFilename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
        }
    }

    @GetMapping("/actions")
    @Operation(summary = "获取所有审计动作", description = "获取系统支持的所有审计动作类型")
    public ResponseEntity<Result<Object>> getAuditActions() {
        return ResponseEntity.ok(Result.success(AuditActionEnums.values()));
    }

    @GetMapping("/levels")
    @Operation(summary = "获取所有审计级别", description = "获取系统支持的所有审计级别")
    public ResponseEntity<Result<Object>> getAuditLevels() {
        return ResponseEntity.ok(Result.success(AuditLevelEnums.values()));
    }
}
