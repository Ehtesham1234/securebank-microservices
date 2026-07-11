package com.ehtesham.securebank.auth.service;

import com.ehtesham.securebank.auth.dto.*;
import com.ehtesham.securebank.user.dto.UserResponse;

import java.util.List;

public interface AuthService {
    UserResponse register(RegisterRequest request, String clientIp);
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(RefreshTokenRequest request);
    void forgotPassword(EmailOnlyRequest request);    // ← new
    void resetPassword(ResetPasswordRequest request);

    UserResponse createStaffUser(CreateStaffRequest request);// ← new
    void sendEmailVerificationOtp(String email);
    void verifyEmail(String email, String otp);
    List<ActiveSessionResponse> getActiveSessions(Long userId);
    void revokeSession(Long userId, String tokenFamily);
    void logoutAllDevices(Long userId);
}
