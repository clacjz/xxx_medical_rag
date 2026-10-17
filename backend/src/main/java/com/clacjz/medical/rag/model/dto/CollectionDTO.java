package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDTO {

    private String id;
    private String name;
    private String description;
    private String ownerId;
    private String ownerName;
    private String departmentId;
    private String departmentName;
    private String type;
    private String status;
    private Integer documentCount;
    private Long chunkCount;
    private Long totalSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CollectionDTO fromEntity(Collection collection) {
        String embeddingModel = null;
        if (collection.getConfig() != null && collection.getConfig().getVector() != null) {
            embeddingModel = collection.getConfig().getVector().getEmbeddingModel();
        }

        return CollectionDTO.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .ownerId(collection.getOwnerId())
                .departmentId(collection.getDepartmentId())
                .type(collection.getType() != null ? collection.getType().name() : null)
                .status(collection.getStatus() != null ? collection.getStatus().name() : null)
                .documentCount(collection.getDocumentCount())
                .chunkCount(collection.getChunkCount())
                .totalSize(collection.getTotalSize())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }
}
