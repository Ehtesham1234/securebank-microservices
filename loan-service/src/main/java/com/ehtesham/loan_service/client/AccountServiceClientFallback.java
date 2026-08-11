package com.ehtesham.loan_service.client;

import com.ehtesham.loan_service.dto.AccountValidationResponse;
import com.ehtesham.loan_service.dto.EmiDebitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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

    // C4 fix: unlike validateAccount (a read), this is a WRITE — silently
    // returning a fake "success" here would mean payEmi() proceeds to
    // mark the EMI paid while no money actually moved, which is exactly
    // the bug this whole fix exists to close. Must fail loudly instead.
    @Override
    public EmiDebitResponse debitForEmi(
            Long accountId, Long userId, Long loanId,
            Integer emiNumber, BigDecimal amount, String description) {

        log.error("Circuit breaker: account-service unavailable. " +
                "Could not debit accountId={} for loanId={} emiNumber={}",
                accountId, loanId, emiNumber);
        throw new RuntimeException(
                "Payment service temporarily unavailable. " +
                        "Please try again.");
    }
}