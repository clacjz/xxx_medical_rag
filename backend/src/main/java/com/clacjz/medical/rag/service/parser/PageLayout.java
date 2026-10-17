package com.clacjz.medical.rag.service.parser;

import lombok.Builder;

/**
 * PDF 页面布局信息，记录每页的文本内容和位置
 */
@Builder
public record PageLayout(
    int pageNumber,    // 1-based 页码
    String text,       // 该页文本内容
    int startPos,      // 该页文本在全文中的起始位置
    int endPos         // 该页文本在全文中的结束位置
) {}