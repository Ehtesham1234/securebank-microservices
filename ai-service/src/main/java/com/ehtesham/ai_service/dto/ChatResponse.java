package com.ehtesham.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ChatResponse {
    private String answer;
    private String conversationId;
    private Long userId;
}
