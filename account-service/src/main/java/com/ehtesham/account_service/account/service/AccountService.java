package com.ehtesham.account_service.service;


import com.ehtesham.account_service.dto.*;
import com.ehtesham.account_service.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    // called internally when KYC verified
    // userId passed as Long instead of User entity
    AccountResponse createSavingsAccount(Long userId,
                                         String firstName, String lastName);

    AccountResponse applyForAccount(
            AccountApplicationRequest request, Long userId);

    List<AccountResponse> getMyAccounts(Long userId);

    AccountResponse getAccountById(Long id, Long userId);

    List<AccountResponse> getAllAccounts();

    AccountResponse freezeAccount(Long id);

    AccountResponse unfreezeAccount(Long id);

    AccountResponse closeAccount(Long id);

    Account getOwnedAccount(Long accountId, Long userId);

    AccountValidationResponse validateAccount(
            Long accountId, Long userId);

    void processCreditForLoan(Long loanId, Long accountId,
                              BigDecimal amount, String loanRef);
}