package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("collection_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("collection_id")
    private String collectionId;

    @TableField(exist = false)
    private Collection collection;

    @TableField("permission_type")
    private CollectionPermissionTypeEnums permissionType;

    @TableField("granted_by")
    private String grantedBy;

    @TableField(exist = false)
    private User grantedByUser;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}