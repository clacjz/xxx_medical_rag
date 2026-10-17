package com.clacjz.medical.rag.model.enums;

public enum CollectionStatus {
    ACTIVE("活跃", "正常运行状态"),
    PAUSED("暂停", "暂停服务状态"),
    ARCHIVED("归档", "已归档不再使用"),
    DELETED("已删除", "标记删除状态"),
    CREATING("创建中", "初始化创建状态"),
    ERROR("异常", "运行异常状态");

    private final String label;
    private final String description;

    CollectionStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
