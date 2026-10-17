package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@TableName("document_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("document_id")
    private String documentId;

    @TableField(exist = false)
    private Document document;

    @TableField("permission_type")
    private PermissionTypeEnums permissionType;

    // 新增字段
    @TableField("department_ids")
    private String departmentIds;  // 逗号分隔的部门ID列表，COMPANY_WIDE时为"0"

    @TableField("direct_user_ids")
    private String directUserIds;  // 逗号分隔的直接授权用户ID列表


    @TableField("granted_by")
    private String grantedBy;

    @TableField(exist = false)
    private User granter;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // 新增方法

    /**
     * 获取部门ID列表。
     * 过滤掉空字符串（防止逗号分割产生空条目）。
     */
    public List<String> getDepartmentIdList() {
        if (departmentIds == null || departmentIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(departmentIds.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 设置部门ID列表
     */
    public void setDepartmentIdList(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            this.departmentIds = null;
        } else {
            this.departmentIds = String.join(",", ids);
        }
    }

    /**
     * 获取直接授权用户ID列表。
     * 注意：返回的列表是可变的ArrayList，调用方可直接修改。
     * 过滤掉空字符串（防止逗号分割产生空条目）。
     */
    public List<String> getDirectUserIdList() {
        if (directUserIds == null || directUserIds.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(directUserIds.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 设置直接授权用户ID列表
     */
    public void setDirectUserIdList(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            this.directUserIds = null;
        } else {
            this.directUserIds = String.join(",", ids);
        }
    }

    /**
     * 校验直接用户数量是否超限（10人限制）
     */
    public boolean isDirectUserLimitExceeded() {
        return getDirectUserIdList().size() > 10;
    }
}