package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.TransactionSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// H3 fix: transaction history lives in account-service, not
// securebank-api — "securebank-api" doesn't expose this route at all, so
// every call here was falling straight to the circuit breaker fallback.
@FeignClient(
        name = "account-service",
        contextId = "transactionServiceClient",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = TransactionServiceClientFallback.class)
public interface TransactionServiceClient {

    // H3 fix: real path is /api/v1/transactions/accounts/{accountId}, and
    // the real response is ApiResponse<Page<TransactionResponse>> — not a
    // bare list.
    @GetMapping("/api/v1/transactions/accounts/{accountId}")
    ApiEnvelope<PageContent<TransactionSummary>> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);
}
