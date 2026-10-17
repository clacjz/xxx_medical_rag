package com.clacjz.medical.rag.model.dto.qdrant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionStats {

    private String name;
    private Long vectorsCount;
    private Long pointsCount;
    private Long indexedVectorsCount;
    private String status;
    private Long createdAt;
}
