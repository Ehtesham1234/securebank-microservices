package com.ehtesham.ai_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 1000, message = "Question cannot exceed 1000 characters")
    private String question;

    /**
     * Optional — client can supply a conversationId to maintain a named
     * conversation. If absent, defaults to the userId so each user has ONE
     * persistent conversation thread. This is intentional: a banking
     * assistant doesn't need multiple threads — one continuous context per
     * user is correct.
     */
    private String conversationId;
}
