package com.clacjz.medical.rag.model.dto.qdrant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumableBatchRequest {

    @NotNull(message = "Collection名称不能为空")
    private String collectionName;

    @NotEmpty(message = "向量数据列表不能为空")
    private List<VectorBatchItem> items;

    private String batchId;

    private boolean waitForCompletion = false;
}
