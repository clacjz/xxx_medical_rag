package com.clacjz.medical.rag.model.enums;

public enum CollectionPermissionLevelEnums {
    NONE(0, "无权限"),
    READ(1, "查看"),
    EDIT(2, "编辑"),
    MANAGE(3, "管理"),
    ADMIN(4, "管理员");

    private final int level;
    private final String description;

    CollectionPermissionLevelEnums(int level, String description) {
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
