package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.PermissionAuditLog;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionAuditLogDTO {

    private String id;
    private OperatorDTO operator;
    private AuditOperationTypeEnums operationType;
    private String operationTypeDescription;
    private AuditTargetTypeEnums targetType;
    private String targetTypeDescription;
    private String targetId;
    private String targetName;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private String ipAddress;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatorDTO {
        private String id;
        private String username;
        private String displayName;
    }

    public static PermissionAuditLogDTO fromEntity(PermissionAuditLog log) {
        return PermissionAuditLogDTO.builder()
                .id(log.getId())
                .operationType(log.getOperationType())
                .operationTypeDescription(log.getOperationType().getDescription())
                .targetType(log.getTargetType())
                .targetTypeDescription(log.getTargetType().getDescription())
                .targetId(log.getTargetId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static PermissionAuditLogDTO fromEntityWithDetails(PermissionAuditLog log, String operatorUsername, String operatorDisplayName, String targetName) {
        PermissionAuditLogDTO dto = fromEntity(log);
        dto.setOperator(OperatorDTO.builder()
                .id(log.getOperatorId())
                .username(operatorUsername)
                .displayName(operatorDisplayName)
                .build());
        dto.setTargetName(targetName);
        return dto;
    }
}
