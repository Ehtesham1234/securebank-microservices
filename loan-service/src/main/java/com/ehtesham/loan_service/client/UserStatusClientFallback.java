package com.ehtesham.loan_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserStatusClientFallback implements UserStatusClient {

    private static final Logger log =
            LoggerFactory.getLogger(UserStatusClientFallback.class);

    @Override
    public InternalUserStatusResponse getUser(Long userId) {
        log.warn("Circuit breaker: securebank-api unavailable for live " +
                "status check on userId={}", userId);
        throw new UserStatusCheckUnavailableException(
                "Could not verify current account status for userId=" + userId);
    }
}
