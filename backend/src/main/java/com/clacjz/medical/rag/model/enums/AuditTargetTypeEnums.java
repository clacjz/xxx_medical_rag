package com.clacjz.medical.rag.model.enums;

public enum AuditTargetTypeEnums {
    USER("用户"),
    ROLE("角色"),
    DEPARTMENT("部门"),
    DOCUMENT("文档");

    private final String description;

    AuditTargetTypeEnums(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
