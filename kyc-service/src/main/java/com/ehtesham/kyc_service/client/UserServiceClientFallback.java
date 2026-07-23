package com.ehtesham.kyc_service.client;

import com.ehtesham.kyc_service.dto.InternalUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserServiceClientFallback
        implements UserServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public void activateUser(Long userId) {
        log.error("Circuit breaker: securebank-api unavailable. " +
                "Could not activate userId={}", userId);
        // Throw so KYC verification fails clearly
        // rather than silently completing with inactive user
        throw new RuntimeException(
                "User activation service temporarily unavailable. " +
                        "Please try again.");
    }

    @Override
    public InternalUserResponse getUserById(Long userId) {
        throw new RuntimeException(
                "User Not Found.");
    }

}