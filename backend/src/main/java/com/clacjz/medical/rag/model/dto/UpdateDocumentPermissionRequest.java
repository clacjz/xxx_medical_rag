package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
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
public class UpdateDocumentPermissionRequest {

    @NotNull(message = "权限类型不能为空")
    private PermissionTypeEnums permissionType;

    // 新增字段
    private List<String> departmentIds;   // 部门ID列表
    private List<String> directUserIds;  // 直接授权用户ID列表
    private Boolean notifyQdrant;         // 是否通知Qdrant更新metadata


    private List<String> allowedUserIds;  // 保留，向后兼容

    private LocalDateTime expiresAt;
}

