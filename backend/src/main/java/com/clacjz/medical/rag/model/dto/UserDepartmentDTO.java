package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.UserDepartment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDepartmentDTO {

    private String id;
    private String userId;
    private String username;
    private String displayName;
    private String departmentId;
    private String departmentName;
    private Boolean isPrimary;
    private String position;
    private LocalDateTime joinedAt;

    public static UserDepartmentDTO fromEntity(UserDepartment userDepartment) {
        return UserDepartmentDTO.builder()
                .id(userDepartment.getId())
                .userId(userDepartment.getUserId())
                .departmentId(userDepartment.getDepartmentId())
                .isPrimary(userDepartment.getIsPrimary())
                .position(userDepartment.getPosition())
                .joinedAt(userDepartment.getJoinedAt())
                .build();
    }

    public static UserDepartmentDTO fromEntityWithDetails(UserDepartment userDepartment, String username, String displayName, String departmentName) {
        UserDepartmentDTO dto = fromEntity(userDepartment);
        dto.setUsername(username);
        dto.setDisplayName(displayName);
        dto.setDepartmentName(departmentName);
        return dto;
    }
}
