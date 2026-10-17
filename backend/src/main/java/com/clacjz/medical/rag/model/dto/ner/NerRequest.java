package com.clacjz.medical.rag.model.dto.ner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NER提取请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerRequest {
    private String text;
    private List<String> entityTypes;
}
