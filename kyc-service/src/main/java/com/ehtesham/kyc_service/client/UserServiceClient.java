package com.ehtesham.kyc_service.client;

import com.ehtesham.kyc_service.dto.InternalUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "securebank-api",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * Activates user after KYC verification.
     * Called by kyc-service → securebank-api internal endpoint.
     */
    @PutMapping("/api/v1/internal/users/{userId}/activate")
    void activateUser(@PathVariable Long userId);

    // Bug fix: compensating action for the saga gap where
    // activateUser() commits independently (a separate service, a
    // separate transaction) before kyc-service's own KYC-verification
    // transaction finishes. If a LATER step (provisioning the savings
    // account/debit card) then fails and kyc-service rolls back, the KYC
    // record correctly reverts to PENDING — but the remote activation
    // had already committed, leaving the user ACTIVE with no account and
    // no verified KYC. Called from that failure path to put the user
    // back where they started.
    @PutMapping("/api/v1/internal/users/{userId}/revert-to-pending-kyc")
    void revertToPendingKyc(@PathVariable Long userId);

    @GetMapping("/api/v1/internal/users/{userId}")
    InternalUserResponse getUserById(@PathVariable Long userId);
}