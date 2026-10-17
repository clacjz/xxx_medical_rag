package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCollectionRequest {

    @Size(min = 1, max = 128, message = "Collection名称长度必须在1-128之间")
    private String name;

    @Size(max = 1024, message = "描述长度不能超过1024")
    private String description;

    private CollectionType type;

    private CollectionStatus status;

    private String departmentId;
}
