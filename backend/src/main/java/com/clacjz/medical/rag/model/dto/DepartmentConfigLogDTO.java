package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.DepartmentConfigLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentConfigLogDTO {

    private String id;
    private String operatorId;
    private String operatorUsername;
    private String targetType;
    private String targetId;
    private String operationType;
    private String beforeValue;
    private String afterValue;
    private String description;
    private LocalDateTime createdAt;

    public static DepartmentConfigLogDTO fromEntity(DepartmentConfigLog log) {
        return DepartmentConfigLogDTO.builder()
                .id(log.getId())
                .operatorId(log.getOperatorId())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .operationType(log.getOperationType())
                .beforeValue(log.getBeforeValue())
                .afterValue(log.getAfterValue())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static DepartmentConfigLogDTO fromEntityWithOperator(
            DepartmentConfigLog log, String operatorUsername) {
        DepartmentConfigLogDTO dto = fromEntity(log);
        dto.setOperatorUsername(operatorUsername);
        return dto;
    }
}
