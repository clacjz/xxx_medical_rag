package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolePermissionsRequest {

    @NotBlank(message = "权限配置不能为空")
    @Size(max = 2000, message = "权限配置长度不能超过2000")
    private String permissions;

    private Boolean auditLogEnabled;
}
