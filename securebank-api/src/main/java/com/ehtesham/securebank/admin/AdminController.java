package com.ehtesham.securebank.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    @GetMapping("/api/v1/admin/test")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminTest() {
        return "Welcome Admin";
    }

    // C3 fix: /api/v1/auth/generate-hash deleted entirely. It was an
    // unauthenticated BCrypt-hashing oracle — publicly reachable (its
    // path matched the /api/v1/auth/** permitAll pattern), no
    // @PreAuthorize, and explicitly commented "DELETE after use" but
    // never removed. Also a free CPU-exhaustion lever since BCrypt is
    // deliberately expensive and this had no rate limit.
}