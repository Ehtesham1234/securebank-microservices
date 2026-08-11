package com.ehtesham.loan_service.client;


import com.ehtesham.loan_service.dto.AccountValidationResponse;
import com.ehtesham.loan_service.dto.EmiDebitResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(
        name = "account-service",
        configuration = IdentityForwardingFeignConfig.class,
        fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @GetMapping("/api/v1/internal/accounts/{accountId}/validate")
    AccountValidationResponse validateAccount(
            @PathVariable Long accountId,
            @RequestParam Long userId);

    // C4 fix: synchronous debit for EMI payment — see
    // account-service's MoneyMovementExecutor.doEmiDebit for the
    // idempotency/ledger details.
    @PostMapping("/api/v1/internal/accounts/{accountId}/debit-for-emi")
    EmiDebitResponse debitForEmi(
            @PathVariable Long accountId,
            @RequestParam Long userId,
            @RequestParam Long loanId,
            @RequestParam Integer emiNumber,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description);
}