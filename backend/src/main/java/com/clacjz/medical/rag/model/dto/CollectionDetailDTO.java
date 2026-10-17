package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDetailDTO {

    private String id;
    private String name;
    private String description;
    private CollectionType type;
    private String typeLabel;
    private CollectionConfig config;
    private CollectionStatus status;
    private String statusLabel;
    private Integer documentCount;
    private Long chunkCount;
    private Long totalSize;
    private String departmentId;
    private String departmentName;
    private String ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTypeLabel() {
        return type != null ? type.getLabel() : null;
    }

    public String getStatusLabel() {
        return status != null ? status.getLabel() : null;
    }
}
