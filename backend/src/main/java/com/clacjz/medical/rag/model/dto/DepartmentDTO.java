package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.Department;
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
public class DepartmentDTO {

    private String id;
    private String code;
    private String name;
    private String parentId;
    private String managerId;
    private String managerName;
    private String description;
    private String status;
    private Boolean isDefault;
    private Long userCount;
    private List<DepartmentDTO> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DepartmentDTO fromEntity(Department department) {
        return DepartmentDTO.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .parentId(department.getParentId())
                .managerId(department.getManagerId())
                .description(department.getDescription())
                .status(department.getStatus())
                .isDefault(department.getIsDefault())
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    public static DepartmentDTO fromEntityWithChildren(Department department, List<Department> children) {
        DepartmentDTO dto = fromEntity(department);
        if (children != null && !children.isEmpty()) {
            dto.setChildren(children.stream()
                    .map(DepartmentDTO::fromEntity)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
