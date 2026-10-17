package com.clacjz.medical.rag.model.dto.ner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NER提取的单个实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerEntity {
    private String text;
    private String type;
    private List<String> aliases;
}
