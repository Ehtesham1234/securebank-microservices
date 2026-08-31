package com.ehtesham.loan_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Bug fix: loan-service used to rely entirely on the gateway-stamped
 * X-User-Status header (baked into the JWT at issuance, up to
 * jwt.expiration — 15 minutes by default — stale) when deciding whether
 * to let someone apply for a loan or pay an EMI. account-service already
 * closes this gap for deposit/withdraw/transfer via an equivalent client
 * (see its UserStatusClient) — this mirrors that here so a user
 * suspended mid-session can't keep applying for credit or paying down a
 * loan for the rest of their token's lifetime.
 */
@FeignClient(
        name = "securebank-api",
        contextId = "userStatusServiceClient",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = UserStatusClientFallback.class)
public interface UserStatusClient {

    @GetMapping("/api/v1/internal/users/{userId}")
    InternalUserStatusResponse getUser(@PathVariable("userId") Long userId);
}
