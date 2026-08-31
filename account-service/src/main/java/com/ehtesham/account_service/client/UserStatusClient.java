package com.ehtesham.account_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * M5 fix: money-movement operations (deposit/withdraw/transfer) check the
 * user's CURRENT status here instead of relying solely on the
 * X-User-Status header, which reflects whatever the status was at JWT
 * issuance and can be stale for the token's full lifetime (up to
 * jwt.expiration — 15 minutes by default) if an admin suspends the
 * account in between.
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
