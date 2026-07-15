package com.ehtesham.loan_service.client;

import com.ehtesham.loan_service.dto.AccountValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccountServiceClientFallback
        implements AccountServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(AccountServiceClientFallback.class);

    @Override
    public AccountValidationResponse validateAccount(
            Long accountId, Long userId) {

        log.warn("Circuit breaker activated for account-service. " +
                "AccountId={}, UserId={}", accountId, userId);

        // Return a safe fallback — treat account as unavailable
        // rather than crashing loan-service
        return AccountValidationResponse.builder()
                .accountId(accountId)
                .valid(false)
                .unavailable(true)   // special flag: service is down, not invalid
                .reason("Account service temporarily unavailable")
                .build();
    }
}