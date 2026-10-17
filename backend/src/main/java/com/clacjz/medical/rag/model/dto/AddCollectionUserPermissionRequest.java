package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.CollectionPermissionLevelEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCollectionUserPermissionRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotNull(message = "权限级别不能为空")
    private CollectionPermissionLevelEnums permissionLevel;

    private LocalDateTime expiresAt;
}
