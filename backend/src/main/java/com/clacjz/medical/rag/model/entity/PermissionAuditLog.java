package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "permission_audit_logs", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionAuditLog {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("operator_id")
    private String operatorId;

    @TableField(exist = false)
    private User operator;

    @TableField("operation_type")
    private AuditOperationTypeEnums operationType;

    @TableField("target_type")
    private AuditTargetTypeEnums targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("old_value")
    private String oldValueJson;

    @TableField("new_value")
    private String newValueJson;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // 瞬时字段
    @TableField(exist = false)
    private Map<String, Object> oldValue;

    @TableField(exist = false)
    private Map<String, Object> newValue;

    public Map<String, Object> getOldValue() {
        if (oldValue == null && oldValueJson != null && !oldValueJson.isBlank()) {
            try {
                oldValue = objectMapper.readValue(oldValueJson, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse oldValue JSON", e);
            }
        }
        return oldValue;
    }

    public void setOldValue(Map<String, Object> oldValue) {
        this.oldValue = oldValue;
        if (oldValue != null) {
            try {
                this.oldValueJson = objectMapper.writeValueAsString(oldValue);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize oldValue to JSON", e);
            }
        }
    }

    public Map<String, Object> getNewValue() {
        if (newValue == null && newValueJson != null && !newValueJson.isBlank()) {
            try {
                newValue = objectMapper.readValue(newValueJson, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse newValue JSON", e);
            }
        }
        return newValue;
    }

    public void setNewValue(Map<String, Object> newValue) {
        this.newValue = newValue;
        if (newValue != null) {
            try {
                this.newValueJson = objectMapper.writeValueAsString(newValue);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize newValue to JSON", e);
            }
        }
    }
}