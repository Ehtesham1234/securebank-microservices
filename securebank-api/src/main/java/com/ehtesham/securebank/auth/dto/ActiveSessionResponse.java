package com.ehtesham.securebank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveSessionResponse {

    private String tokenFamily;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean currentSession;   // true if THIS is the session making the request
}