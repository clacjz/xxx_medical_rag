package com.clacjz.medical.rag.model.enums;

public enum ChunkingStrategy {
    CHARACTER("字符分块"),
    SENTENCE("句子分块"),
    PARAGRAPH("段落分块"),
    SEMANTIC("语义分块"),
    FIXED("固定长度分块"),
    NONE("不分块");

    private final String label;

    ChunkingStrategy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
