package com.ehtesham.loan_service.client;


import com.ehtesham.loan_service.dto.AccountValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
@FeignClient(
        name = "account-service",
        fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @GetMapping("/api/v1/internal/accounts/{accountId}/validate")
    AccountValidationResponse validateAccount(
            @PathVariable Long accountId,
            @RequestParam Long userId);
}