package com.clacjz.medical.rag.model.enums;

public enum PermissionLevelEnums {
    READ(1, "读取"),
    COMMENT(2, "评论"),
    EDIT(3, "编辑");

    private final int level;
    private final String description;

    PermissionLevelEnums(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }
}
