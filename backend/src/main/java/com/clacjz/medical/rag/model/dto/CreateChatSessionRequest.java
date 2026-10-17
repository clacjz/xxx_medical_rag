package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatSessionRequest {

    private String title;

    @NotEmpty(message = "至少需要选择一个Collection")
    private List<String> collectionIds;
}
