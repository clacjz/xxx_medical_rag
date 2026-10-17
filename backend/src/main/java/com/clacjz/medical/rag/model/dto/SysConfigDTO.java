package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.SysConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysConfigDTO {

    private String id;
    private String category;
    private String configKey;
    private String value;
    private String valueType;
    private String description;
    private String defaultValue;
    private String validValues;

    public static SysConfigDTO fromEntity(SysConfig entity) {
        if (entity == null) return null;
        return SysConfigDTO.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .configKey(entity.getConfigKey())
                .value(entity.getValue())
                .valueType(entity.getValueType())
                .description(entity.getDescription())
                .defaultValue(entity.getDefaultValue())
                .validValues(entity.getValidValues())
                .build();
    }
}