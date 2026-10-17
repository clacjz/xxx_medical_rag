package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.DocumentPermission;
import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
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
public class DocumentPermissionDTO {

    private String id;
    private String documentId;
    private String documentTitle;
    private String ownerId;
    private String ownerName;
    private PermissionTypeEnums permissionType;
    private String permissionTypeDescription;

    // 新增字段
    private List<String> departmentIds;    // 部门ID列表
    private List<String> directUserIds;    // 直接授权用户ID列表
    private Integer directUserCount;       // 直接用户数量


    private String grantedBy;
    private String granterName;
    private List<AllowedUserDTO> allowedUsers;
    private LocalDateTime grantedAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllowedUserDTO {
        private String userId;
        private String username;
        private String displayName;
        private String departmentName;
        private PermissionLevelEnums permissionLevel;
        private LocalDateTime expiresAt;
    }

    public static DocumentPermissionDTO fromEntity(DocumentPermission permission) {
        return DocumentPermissionDTO.builder()
                .id(permission.getId())
                .documentId(permission.getDocumentId())
                .permissionType(permission.getPermissionType())
                .permissionTypeDescription(permission.getPermissionType().getDescription())
                .departmentIds(permission.getDepartmentIdList())
                .directUserIds(permission.getDirectUserIdList())
                .directUserCount(permission.getDirectUserIdList().size())
                .grantedBy(permission.getGrantedBy())
                .grantedAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}

