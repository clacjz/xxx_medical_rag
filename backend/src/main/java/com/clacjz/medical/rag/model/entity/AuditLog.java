package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("action")
    private AuditActionEnums action;

    @TableField("level")
    @Builder.Default
    private AuditLevelEnums level = AuditLevelEnums.INFO;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("description")
    private String description;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("details")
    private String details;

    @TableField("timestamp")
    private LocalDateTime timestamp;

    @TableField("status")
    @Builder.Default
    private String status = "success";
}