package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.AccountSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(
        name = "account-service",
        contextId = "accountServiceClient",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    // H3 fix: real response is ApiResponse<List<AccountResponse>>, not a
    // bare list — see ApiEnvelope.
    @GetMapping("/api/v1/accounts")
    ApiEnvelope<List<AccountSummary>> getMyAccounts();
}
