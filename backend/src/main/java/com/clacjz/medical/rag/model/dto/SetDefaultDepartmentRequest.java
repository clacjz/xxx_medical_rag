package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetDefaultDepartmentRequest {

    @NotBlank(message = "部门ID不能为空")
    private String departmentId;

    private Boolean autoAssignNewUsers = true;

    private Boolean autoAssignUnassignedUsers = false;
}
