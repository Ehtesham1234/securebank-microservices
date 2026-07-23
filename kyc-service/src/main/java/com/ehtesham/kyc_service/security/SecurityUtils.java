package com.ehtesham.kyc_service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException(
                    "No authenticated user. Request must come " +
                            "through the API gateway.");
        }
        return (Long) auth.getDetails();
    }

    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) return "";
        return auth.getName(); // email set as principal
    }

    public String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return "";
        }
        return auth.getAuthorities()
                .iterator().next().getAuthority();
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(getCurrentUserRole());
    }

    public boolean isTeller() {
        String role = getCurrentUserRole();
        return "ROLE_TELLER".equals(role)
                || "ROLE_ADMIN".equals(role);
    }
}