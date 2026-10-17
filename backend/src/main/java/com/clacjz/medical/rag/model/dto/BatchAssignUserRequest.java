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
public class BatchAssignUserRequest {

    @NotEmpty(message = "用户ID列表不能为空")
    private List<String> userIds;

    private Boolean isPrimary = false;

    private String position;
}
