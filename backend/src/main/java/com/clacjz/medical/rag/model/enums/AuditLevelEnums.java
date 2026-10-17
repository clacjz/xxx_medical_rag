package com.clacjz.medical.rag.model.enums;

public enum AuditLevelEnums {
    INFO("普通操作"),
    WARNING("警告操作"),
    SECURITY("安全审计"),
    CRITICAL("关键操作");

    private final String description;

    AuditLevelEnums(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
