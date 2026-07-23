package com.ehtesham.securebank.auth.controller;

import com.ehtesham.securebank.auth.dto.*;
import com.ehtesham.securebank.auth.service.AuthService;
import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        // Use real client IP, not gateway IP
//        String clientIp = getClientIp(httpRequest);
//        UserResponse response = authService.register(
//                request, clientIp);
        String clientIp = httpRequest.getRemoteAddr();

        UserResponse user = authService.register(request, clientIp);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Login successful", auth));
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse auth = authService.refresh(request);
        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", auth));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {

        authService.logout(request);
        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully"));
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody EmailOnlyRequest request) {

        authService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "If this email is registered, an OTP has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success("Password reset successful"));
    }

//    to use when someone delayed or did not see otp want to generate new otp
    @PostMapping("/email/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationOtp(
            @Valid @RequestBody EmailOnlyRequest request) {
        // reusing EmailOnlyRequest's shape (just an email field)
        // since it's structurally identical — same "don't duplicate
        // a one-field DTO" reasoning

        authService.sendEmailVerificationOtp(request.getEmail());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "If this email is registered and unverified, " +
                                "an OTP has been sent"));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        authService.verifyEmail(request.getEmail(), request.getOtp());

        return ResponseEntity.ok(
                ApiResponse.success("Email verified successfully"));
    }

    // Add this helper method to AuthController:
    private String getClientIp(HttpServletRequest request) {
        // Gateway forwards real client IP in this header
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be comma-separated list
            // first IP is the original client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
