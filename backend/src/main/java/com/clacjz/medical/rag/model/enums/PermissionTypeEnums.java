package com.clacjz.medical.rag.model.enums;

public enum PermissionTypeEnums {
    COMPANY_WIDE("全公司可查看", "公司内所有认证用户", 1),
    DEPARTMENT_WIDE("部门内公开", "文档所属部门的所有成员", 2),
    RESTRICTED("指定人员开放", "上传者指定的特定用户(≤10人)", 3),
    DEPARTMENT_WIDE_RESTRICTED("部门受限", "本部门全员+其他部门最多10名用户", 4);

    private final String description;
    private final String accessScope;
    private final int priority;

    PermissionTypeEnums(String description, String accessScope, int priority) {
        this.description = description;
        this.accessScope = accessScope;
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public String getAccessScope() {
        return accessScope;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 是否支持部门限制
     */
    public boolean supportsDepartmentRestriction() {
        return this == DEPARTMENT_WIDE || this == DEPARTMENT_WIDE_RESTRICTED;
    }

    /**
     * 是否支持直接用户授权
     */
    public boolean supportsDirectUserAccess() {
        return this == RESTRICTED || this == DEPARTMENT_WIDE_RESTRICTED;
    }
}
