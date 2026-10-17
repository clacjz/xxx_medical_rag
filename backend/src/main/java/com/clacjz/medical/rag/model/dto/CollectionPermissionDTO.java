package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionPermission;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
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
public class CollectionPermissionDTO {

    private String id;
    private String collectionId;
    private String collectionName;
    private String ownerId;
    private String ownerName;
    private CollectionPermissionTypeEnums permissionType;
    private String permissionTypeDescription;
    private String grantedBy;
    private String granterName;
    private LocalDateTime grantedAt;
    private LocalDateTime updatedAt;
    private List<AllowedUserDTO> allowedUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllowedUserDTO {
        private String userId;
        private String username;
        private String displayName;
        private String permissionLevel;
        private LocalDateTime expiresAt;
    }

    public static CollectionPermissionDTO fromEntity(CollectionPermission permission, Collection collection) {
        CollectionPermissionDTO dto = CollectionPermissionDTO.builder()
                .id(permission.getId())
                .collectionId(permission.getCollectionId())
                .collectionName(collection != null ? collection.getName() : null)
                .permissionType(permission.getPermissionType())
                .permissionTypeDescription(permission.getPermissionType().getDescription())
                .grantedBy(permission.getGrantedBy())
                .grantedAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();

        return dto;
    }
}
