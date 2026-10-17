package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAccessCheckRequest {

    @NotEmpty(message = "文档ID列表不能为空")
    private List<String> documentIds;
}
