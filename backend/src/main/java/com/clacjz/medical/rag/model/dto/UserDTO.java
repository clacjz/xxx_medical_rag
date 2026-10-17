package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息")
public class UserDTO {

    @Schema(description = "用户ID")
    private String id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "角色")
    private UserRoleEnum role;

    @Schema(description = "状态")
    private UserStatusEnums status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "用户角色列表（新多角色系统）")
    private List<UserRoleDTO> roles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "用户角色信息")
    public static class UserRoleDTO {
        @Schema(description = "角色ID")
        private String id;

        @Schema(description = "角色代码")
        private String code;

        @Schema(description = "角色名称")
        private String name;

        @Schema(description = "角色级别")
        private Integer level;

        @Schema(description = "是否为首选角色")
        private Boolean isPrimary;
    }

    public static UserDTO fromUser(User user) {
        UserDTOBuilder builder = UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        if (user.getUserRoles() != null && !user.getUserRoles().isEmpty()) {
            List<UserRoleDTO> roleDTOs = user.getUserRoles().stream()
                    .map(ur -> {
                        if (ur.getRole() != null) {
                            return UserRoleDTO.builder()
                                    .id(ur.getRole().getId())
                                    .code(ur.getRole().getCode())
                                    .name(ur.getRole().getName())
                                    .level(ur.getRole().getLevel())
                                    .isPrimary(ur.getIsPrimary())
                                    .build();
                        }
                        return null;
                    })
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());
            builder.roles(roleDTOs);
        }

        return builder.build();
    }
}
