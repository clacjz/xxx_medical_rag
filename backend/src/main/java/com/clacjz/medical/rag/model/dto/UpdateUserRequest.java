package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新用户请求")
public class UpdateUserRequest {

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色")
    private UserRoleEnum role;

    @Schema(description = "状态")
    private UserStatusEnums status;
}
