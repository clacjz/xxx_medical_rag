package com.clacjz.medical.rag.model.enums;

public enum CollectionType {
    KNOWLEDGE_BASE("知识库", "通用知识库类型"),
    QA_PAIRS("问答对", "问答对集合类型"),
    DOCUMENT_SUMMARY("文档摘要", "文档摘要集合类型"),
    TECHNICAL_DOCUMENT("技术文档", "技术文档集合类型"),
    PRODUCT_MANUAL("产品手册", "产品手册集合类型"),
    INTERNAL_KNOWLEDGE("内部知识", "企业内部知识库类型");

    private final String label;
    private final String description;

    CollectionType(String label, String description) {
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
