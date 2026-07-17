package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.AccountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(AccountServiceClientFallback.class);

    @Override
    public List<AccountSummary> getMyAccounts(Long userId) {
        log.warn("Circuit breaker: account-service unavailable for userId={}", userId);
        return List.of();
    }
}
