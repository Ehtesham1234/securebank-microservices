package com.ehtesham.account_service.account.service;

import com.ehtesham.account_service.account.dto.AccountApplicationRequest;
import com.ehtesham.account_service.account.dto.AccountResponse;
import com.ehtesham.account_service.account.dto.AccountValidationResponse;
import com.ehtesham.account_service.account.entity.Account;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    // Called by kyc-service via internal endpoint
    AccountResponse createSavingsAccount(Long userId,
                                         String firstName, String lastName);

    // userId from SecurityContext — not from User entity
    AccountResponse applyForAccount(
            AccountApplicationRequest request, Long userId);

    List<AccountResponse> getMyAccounts(Long userId);

    AccountResponse getAccountById(Long id, Long userId);

    List<AccountResponse> getAllAccounts();

    AccountResponse freezeAccount(Long id);

    AccountResponse unfreezeAccount(Long id);

    AccountResponse closeAccount(Long id);

    // Takes Long userId instead of User entity
    Account getOwnedAccount(Long accountId, Long userId);

    AccountValidationResponse validateAccount(
            Long accountId, Long userId);

    void processCreditForLoan(Long loanId, Long accountId,
                              BigDecimal amount, String loanRef);

    // C9 fix: called by the scheduler — pays out every ACTIVE Fixed
    // Deposit whose maturityDate has arrived into the customer's
    // SAVINGS account, then closes the FD account.
    void processMaturedFixedDeposits();

    // Bug fix: extracted so processMaturedFixedDeposits() can process
    // each FD in its OWN transaction (via a self-injected proxy call —
    // see the implementation) instead of one shared transaction/
    // persistence context for the whole batch, where a flush triggered
    // by a later FD (or by the final commit) could silently roll back
    // FDs that already appeared to succeed earlier in the same run.
    void payOutMaturedFixedDeposit(Long fdId);
}