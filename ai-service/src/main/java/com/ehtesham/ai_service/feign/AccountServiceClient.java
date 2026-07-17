package com.ehtesham.ai_service.feign;

import com.ehtesham.ai_service.dto.AccountSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(
        name = "account-service",
        fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts")
    List<AccountSummary> getMyAccounts(
            @RequestHeader("X-User-Id") Long userId);
}
