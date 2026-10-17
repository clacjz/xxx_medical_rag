package com.clacjz.medical.rag.model.enums;

public enum IndexType {
    FLAT("暴力检索"),
    IVF("倒排索引"),
    HNSW("分层可导航小世界图");

    private final String label;

    IndexType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
