package com.ehtesham.account_service.security;

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
                    "No authenticated user in security context. " +
                            "Request must come through the API gateway.");
        }
        return (Long) auth.getDetails();
    }

    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException(
                    "No authenticated user in security context.");
        }
        return auth.getName();
    }

    public String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null
                || auth.getAuthorities().isEmpty()) {
            return "";
        }
        return auth.getAuthorities()
                .iterator().next()
                .getAuthority();
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(getCurrentUserRole());
    }

    public boolean isTeller() {
        String role = getCurrentUserRole();
        return "ROLE_TELLER".equals(role)
                || "ROLE_ADMIN".equals(role);
    }

    public boolean isCustomer() {
        return "ROLE_CUSTOMER".equals(getCurrentUserRole());
    }
}