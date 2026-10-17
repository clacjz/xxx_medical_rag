package com.clacjz.medical.rag.model.dto.qdrant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {

    @NotNull(message = "向量不能为空")
    private float[] vector;

    @Positive(message = "返回数量必须为正数")
    @Builder.Default
    private int limit = 10;

    private Double scoreThreshold;

    @Builder.Default
    private Integer ef = 128;

    /**
     * Qdrant payload filter for pre-filtering before vector search.
     * Key-value pairs where value is matched against payload fields.
     * Supports String (keyword match) and List&lt;String&gt; (any match) values.
     */
    private Map<String, Object> filter;
}
