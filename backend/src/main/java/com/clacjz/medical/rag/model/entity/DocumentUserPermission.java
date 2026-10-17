package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("document_user_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUserPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("document_id")
    private String documentId;

    @TableField(exist = false)
    private Document document;

    @TableField("user_id")
    private String userId;

    @TableField(exist = false)
    private User user;

    @TableField("permission_level")
    @Builder.Default
    private PermissionLevelEnums permissionLevel = PermissionLevelEnums.READ;

    @TableField("granted_by")
    private String grantedBy;

    @TableField(exist = false)
    private User granter;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}