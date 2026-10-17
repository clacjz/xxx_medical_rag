package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDepartmentRequest {

    @Size(max = 100, message = "部门名称长度不能超过100")
    private String name;

    private String managerId;

    @Size(max = 500, message = "部门描述长度不能超过500")
    private String description;

    private String status;
}
