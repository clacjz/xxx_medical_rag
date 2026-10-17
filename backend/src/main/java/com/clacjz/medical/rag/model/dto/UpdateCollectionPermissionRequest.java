package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import jakarta.validation.constraints.NotNull;
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
public class UpdateCollectionPermissionRequest {

    @NotNull(message = "权限类型不能为空")
    private CollectionPermissionTypeEnums permissionType;

    private List<String> allowedUserIds;

    private LocalDateTime expiresAt;
}
