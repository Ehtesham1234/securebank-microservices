package com.ehtesham.securebank.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    private final PasswordEncoder passwordEncoder;

    public AdminController(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/v1/admin/test")
//    @PreAuthorize("hasRole('ADMIN')")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminTest() {

        System.out.println(
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        return "Welcome Admin";
    }

    // TEMPORARY — add to AdminController, DELETE after use
    @GetMapping("/api/v1/auth/generate-hash")
    public String generateHash(@RequestParam String password) {
        return passwordEncoder.encode(password) ;
    }
}