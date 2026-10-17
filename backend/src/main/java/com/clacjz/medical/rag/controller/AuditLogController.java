package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.PermissionAuditLogDTO;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.PermissionAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log API", description = "审计日志相关接口")
public class AuditLogController {

    private final PermissionAuditService permissionAuditService;

    @GetMapping("/permissions")
    @Operation(summary = "查询权限变更日志", description = "分页查询权限变更审计日志（时间范围必填，Unix时间戳毫秒级）")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> getPermissionAuditLogs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "操作人ID") @RequestParam(required = false) String operatorId,
            @Parameter(description = "目标类型") @RequestParam(required = false) AuditTargetTypeEnums targetType,
            @Parameter(description = "目标ID") @RequestParam(required = false) String targetId,
            @Parameter(description = "操作类型") @RequestParam(required = false) AuditOperationTypeEnums operationType,
            @Parameter(description = "开始时间（必填，Unix时间戳毫秒级）") @RequestParam Long startTime,
            @Parameter(description = "结束时间（必填，Unix时间戳毫秒级）") @RequestParam Long endTime) {

        if (startTime == null || endTime == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "开始时间和结束时间不能为空");
        }

        LocalDateTime startDateTime = LocalDateTime.ofEpochSecond(startTime / 1000, 0, ZoneOffset.ofHours(8));
        LocalDateTime endDateTime = LocalDateTime.ofEpochSecond(endTime / 1000, 0, ZoneOffset.ofHours(8));

        log.info("Query permission audit logs: page={}, pageSize={}, targetType={}, operationType={}, startTime={}, endTime={}",
                page, pageSize, targetType, operationType, startDateTime, endDateTime);

        IPage<PermissionAuditLogDTO> auditLogs = permissionAuditService.getAuditLogs(
                page, pageSize, operatorId, targetType, targetId, operationType, startDateTime, endDateTime);

        PageResult<PermissionAuditLogDTO> result = PageResult.from(auditLogs, dto -> dto);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/permissions/recent")
    @Operation(summary = "获取最近权限变更记录", description = "获取指定对象的最近权限变更记录")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<PermissionAuditLogDTO>>> getRecentAuditLogs(
            @Parameter(description = "目标类型") @RequestParam AuditTargetTypeEnums targetType,
            @Parameter(description = "目标ID") @RequestParam String targetId,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int limit) {

        log.info("Get recent audit logs: targetType={}, targetId={}, limit={}", targetType, targetId, limit);

        List<PermissionAuditLogDTO> logs = permissionAuditService.getRecentAuditLogs(
                targetType.name(), targetId, limit);
        return ResponseEntity.ok(Result.success(logs));
    }
}
