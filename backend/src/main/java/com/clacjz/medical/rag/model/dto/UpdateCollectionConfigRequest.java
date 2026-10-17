package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.config.CollectionConfig;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCollectionConfigRequest {

    @NotNull(message = "配置不能为空")
    private CollectionConfig config;
}
