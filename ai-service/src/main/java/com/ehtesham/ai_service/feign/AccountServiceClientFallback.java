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
    public ApiEnvelope<List<AccountSummary>> getMyAccounts() {
        log.warn("Circuit breaker: account-service unavailable for getMyAccounts");
        ApiEnvelope<List<AccountSummary>> empty = new ApiEnvelope<>();
        empty.setData(List.of());
        return empty;
    }
}
