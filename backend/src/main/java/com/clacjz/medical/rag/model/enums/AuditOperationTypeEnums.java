package com.clacjz.medical.rag.model.enums;

public enum AuditOperationTypeEnums {
    GRANT("授予权限"),
    REVOKE("撤销权限"),
    UPDATE("更新权限");

    private final String description;

    AuditOperationTypeEnums(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
