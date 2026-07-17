package com.ehtesham.ai_service.security;

import lombok.Builder;
import lombok.Getter;

/**
 * Carries verified user identity extracted from gateway headers.
 * NEVER populated from model output — the model cannot supply or
 * override the userId. This is a core security principle: the AI
 * model is untrusted for identity claims.
 */
@Getter
@Builder
public class SecurityContext {

    private final Long userId;
    private final String userEmail;
    private final String userRole;

    public boolean isAdmin() {
        return userRole != null && userRole.contains("ADMIN");
    }

    public boolean isTeller() {
        return userRole != null
                && (userRole.contains("TELLER") || userRole.contains("ADMIN"));
    }
}
