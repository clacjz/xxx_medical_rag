package com.clacjz.medical.rag.model.enums;

public enum UserStatusEnums {
    ACTIVE("正常"),
    DISABLED("已禁用"),
    LOCKED("已锁定");

    private final String description;

    UserStatusEnums(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
