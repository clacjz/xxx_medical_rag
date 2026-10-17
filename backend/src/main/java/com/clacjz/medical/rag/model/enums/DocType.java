package com.clacjz.medical.rag.model.enums;

/**
 * 文档类型枚举
 * 用于区分不同的文档处理策略
 */
public enum DocType {
    /**
     * 说明书类 - 结构化短文档
     * 特征：短小（1-5页）、有固定字段、以OCR为主
     * 处理策略：不切片，整篇存储
     */
    MANUAL("说明书", "结构化短文档，如药品说明书、证书证件等"),

    /**
     * 普通文档 - 非结构化长文档
     * 特征：长度不定、无固定结构
     * 处理策略：语义切片，按chunk存储
     */
    NORMAL("普通文档", "普通文档，如文章、报告、书籍等");

    private final String label;
    private final String description;

    DocType(String label, String description) {
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