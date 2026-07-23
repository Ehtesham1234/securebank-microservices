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
}