package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.AuditLog;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审计日志")
public class AuditLogDTO {

    @Schema(description = "日志ID")
    private String id;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "操作类型")
    private AuditActionEnums action;

    @Schema(description = "操作类型描述")
    private String actionDescription;

    @Schema(description = "审计级别")
    private AuditLevelEnums level;

    @Schema(description = "审计级别描述")
    private String levelDescription;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "资源ID")
    private String resourceId;

    @Schema(description = "操作描述")
    private String description;

    @Schema(description = "IP地址")
    private String ipAddress;

    @Schema(description = "User Agent")
    private String userAgent;

    @Schema(description = "详细信息(JSON)")
    private String details;

    @Schema(description = "操作时间")
    private LocalDateTime timestamp;

    @Schema(description = "状态")
    private String status;

    public static AuditLogDTO fromEntity(AuditLog auditLog) {
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .username(auditLog.getUsername())
                .displayName(auditLog.getDisplayName())
                .action(auditLog.getAction())
                .actionDescription(auditLog.getAction() != null ? auditLog.getAction().getDescription() : null)
                .level(auditLog.getLevel())
                .levelDescription(auditLog.getLevel() != null ? auditLog.getLevel().getDescription() : null)
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .description(auditLog.getDescription())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(auditLog.getDetails())
                .timestamp(auditLog.getTimestamp())
                .status(auditLog.getStatus())
                .build();
    }
}
