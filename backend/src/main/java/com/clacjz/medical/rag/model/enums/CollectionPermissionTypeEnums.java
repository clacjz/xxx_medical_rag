package com.clacjz.medical.rag.model.enums;

public enum CollectionPermissionTypeEnums {
    PUBLIC("公开访问", "所有认证用户可查看", 1),
    DEPARTMENT_WIDE("部门内公开", "本部门所有成员可查看", 2),
    RESTRICTED("指定人员开放", "仅指定用户可查看(≤10人)", 3),
    DEPARTMENT_WIDE_RESTRICTED("部门受限", "本部门全员+其他部门最多10名用户", 4);

    private final String description;
    private final String accessScope;
    private final int priority;

    CollectionPermissionTypeEnums(String description, String accessScope, int priority) {
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
