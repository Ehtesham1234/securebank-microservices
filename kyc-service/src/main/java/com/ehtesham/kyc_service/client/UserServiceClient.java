package com.ehtesham.kyc_service.client;

import com.ehtesham.kyc_service.dto.InternalUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "securebank-api",
        fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * Activates user after KYC verification.
     * Called by kyc-service → securebank-api internal endpoint.
     */
    @PutMapping("/api/v1/internal/users/{userId}/activate")
    void activateUser(@PathVariable Long userId);
    @GetMapping("/api/v1/internal/users/{userId}")
    InternalUserResponse getUserById(@PathVariable Long userId);
}