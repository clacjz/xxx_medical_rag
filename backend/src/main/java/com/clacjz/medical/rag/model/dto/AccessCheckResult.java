package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessCheckResult {

    private boolean hasAccess;
    private PermissionLevelEnums permissionLevel;
    private String reason;

    // 新增字段
    private String accessedDeptId;   // 本次访问使用的部门身份
    private String matchReason;      // 匹配原因：system_admin/owner/company_wide/department_intersection/direct_user


    public static AccessCheckResult allowed(String reason) {
        return AccessCheckResult.builder()
                .hasAccess(true)
                .permissionLevel(PermissionLevelEnums.READ)
                .reason(reason)
                .build();
    }

    public static AccessCheckResult denied(String reason) {
        return AccessCheckResult.builder()
                .hasAccess(false)
                .permissionLevel(null)
                .reason(reason)
                .build();
    }

    public static AccessCheckResult allowed(String reason, PermissionLevelEnums level) {
        return AccessCheckResult.builder()
                .hasAccess(true)
                .permissionLevel(level)
                .reason(reason)
                .build();
    }

    // 新增静态工厂方法
    public static AccessCheckResult allowed(String reason, PermissionLevelEnums level,
                                            String accessedDeptId, String matchReason) {
        return AccessCheckResult.builder()
                .hasAccess(true)
                .permissionLevel(level)
                .reason(reason)
                .accessedDeptId(accessedDeptId)
                .matchReason(matchReason)
                .build();
    }
}

