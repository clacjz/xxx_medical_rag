package com.clacjz.medical.rag.model.dto.ner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NER提取响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerResponse {
    private List<NerEntity> entities;
}
