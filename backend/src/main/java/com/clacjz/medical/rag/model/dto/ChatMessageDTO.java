package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private String id;
    private String sessionId;
    private String role;
    private String content;
    private String reasoning;
    private List<ChatMessage.SourceReference> sources;
    private ChatMessage.QueryParams queryParams;
    private ChatMessage.ModelInfo modelInfo;
    private ChatMessage.TokenUsage tokenUsage;
    private Integer generationTimeMs;
    private LocalDateTime createdAt;
}
