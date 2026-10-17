package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {

    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    @Min(value = 1, message = "角色级别最小为1")
    @Max(value = 4, message = "角色级别最大为4")
    private Integer level;

    private String permissions;

    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

    private Boolean active;
}
