package com.clacjz.medical.rag.model.dto.qdrant;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorBatchItem {

    @NotNull(message = "向量数据不能为空")
    private float[] vector;

    private Map<String, Object> payload;

    private String pointId;
}
