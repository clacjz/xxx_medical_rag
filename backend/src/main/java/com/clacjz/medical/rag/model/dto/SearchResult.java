package com.clacjz.medical.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String id;
    private String documentId;
    private String collectionId;
    private String text;
    private Double score;
    private String title;
    private String snippet;
    private Map<String, Object> metadata;

    private Integer startPage;   // 起始页（1-based，0表示无页码信息）
    private Integer endPage;     // 结束页

    public static SearchResult fromQdrantResult(String id, String collectionId, String text, Double score,
                                                Map<String, Object> metadata) {
        return SearchResult.builder()
                .id(id)
                .collectionId(collectionId)
                .text(text)
                .score(score)
                .metadata(metadata)
                .build();
    }

    /**
     * 创建副本并替换score值，避免手动逐字段拷贝遗漏新增字段
     */
    public SearchResult withScore(double newScore) {
        return SearchResult.builder()
                .id(this.id)
                .documentId(this.documentId)
                .collectionId(this.collectionId)
                .text(this.text)
                .score(newScore)
                .title(this.title)
                .snippet(this.snippet)
                .startPage(this.startPage)
                .endPage(this.endPage)
                .metadata(this.metadata)
                .build();
    }
}
