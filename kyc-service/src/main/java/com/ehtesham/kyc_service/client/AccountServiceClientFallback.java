package com.ehtesham.kyc_service.client;

import com.ehtesham.kyc_service.dto.AccountSetupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccountServiceClientFallback
        implements AccountServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AccountServiceClientFallback.class);

    @Override
    public AccountSetupResponse kycSetup(
            Long userId, String firstName, String lastName) {
        log.error("Circuit breaker: account-service unavailable. " +
                "Could not create account for userId={}", userId);
        throw new RuntimeException(
                "Account creation service temporarily unavailable. " +
                        "Please try again.");
    }
}