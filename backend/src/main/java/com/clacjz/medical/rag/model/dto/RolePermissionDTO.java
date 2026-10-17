package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionDTO {

    private String id;
    private String code;
    private String name;
    private Integer level;
    private String permissions;
    private List<String> permissionList;
    private String description;
    private Boolean active;
    private Long userCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RolePermissionDTO fromEntity(Role role) {
        return RolePermissionDTO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .level(role.getLevel())
                .permissions(role.getPermissions())
                .description(role.getDescription())
                .active(role.getActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public static RolePermissionDTO fromEntityWithUserCount(Role role, long userCount) {
        RolePermissionDTO dto = fromEntity(role);
        dto.setUserCount(userCount);
        return dto;
    }
}
