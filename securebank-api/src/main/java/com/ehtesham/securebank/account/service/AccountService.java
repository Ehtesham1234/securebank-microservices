package com.ehtesham.securebank.account.service;

import com.ehtesham.securebank.account.dto.AccountApplicationRequest;
import com.ehtesham.securebank.account.dto.AccountResponse;
import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.user.entity.User;

import java.util.List;

public interface AccountService {

    // called internally when KYC verified
    AccountResponse createSavingsAccount(User user);

    // CUSTOMER — apply for additional account
    AccountResponse applyForAccount(
            AccountApplicationRequest request,
            String email);

    // CUSTOMER — get my accounts
    List<AccountResponse> getMyAccounts(String email);

    // CUSTOMER — get account by id
    AccountResponse getAccountById(Long id, String email);

    // ADMIN
    List<AccountResponse> getAllAccounts();

    AccountResponse freezeAccount(Long id);

    AccountResponse unfreezeAccount(Long id);

    AccountResponse closeAccount(Long id);

    Account getOwnedAccount(Long accountId, User user);
}