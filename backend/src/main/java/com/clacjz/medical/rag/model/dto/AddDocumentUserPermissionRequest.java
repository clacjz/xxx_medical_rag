package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDocumentUserPermissionRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @Builder.Default
    private PermissionLevelEnums permissionLevel = PermissionLevelEnums.READ;

    private LocalDateTime expiresAt;
}

