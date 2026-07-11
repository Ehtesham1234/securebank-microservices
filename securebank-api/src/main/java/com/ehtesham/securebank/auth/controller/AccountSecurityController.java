package com.ehtesham.securebank.auth.controller;

import com.ehtesham.securebank.auth.dto.ActiveSessionResponse;
import com.ehtesham.securebank.auth.dto.CreateStaffRequest;
import com.ehtesham.securebank.auth.service.AuthService;
import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account-security")
@Tag(name = "Accounts-security", description = "Account security management and operations")
public class AccountSecurityController {

    private final AuthService authService;

    public AccountSecurityController(AuthService authService) {
        this.authService = authService;
    }


    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ActiveSessionResponse>>> getActiveSessions(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        List<ActiveSessionResponse> sessions =
                authService.getActiveSessions(principal.getUserId());

        return ResponseEntity.ok(
                ApiResponse.success("Active sessions retrieved", sessions));
    }

    @DeleteMapping("/sessions/{tokenFamily}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable String tokenFamily,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        authService.revokeSession(principal.getUserId(), tokenFamily);

        return ResponseEntity.ok(
                ApiResponse.success("Session revoked successfully"));
    }

    @PostMapping("/logout-all-devices")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        authService.logoutAllDevices(principal.getUserId());

        return ResponseEntity.ok(
                ApiResponse.success("Logged out from all devices"));
    }

    @PostMapping("/admin/users/create-staff")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createStaffUser(
            @Valid @RequestBody CreateStaffRequest request) {

        UserResponse response = authService.createStaffUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Staff account created successfully", response));
    }
}
