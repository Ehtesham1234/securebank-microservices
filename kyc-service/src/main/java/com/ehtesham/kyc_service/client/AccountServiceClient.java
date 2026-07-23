package com.ehtesham.kyc_service.client;

import com.ehtesham.kyc_service.dto.AccountSetupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "account-service",
        fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    /**
     * Creates savings account + debit card for a newly
     * KYC-verified customer. Single atomic call to account-service.
     */
    @PostMapping("/api/v1/internal/accounts/kyc-setup")
    AccountSetupResponse kycSetup(
            @RequestParam Long userId,
            @RequestParam String firstName,
            @RequestParam String lastName);
}