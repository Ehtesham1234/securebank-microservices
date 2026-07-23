package com.ehtesham.account_service.account.repository;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.enums.AccountStatus;
import com.ehtesham.account_service.account.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    List<Account> findByUserId(Long userId);

    List<Account> findByUserIdAndAccountStatus(
            Long userId, AccountStatus status);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByUserIdAndAccountType(
            Long userId, AccountType accountType);

    List<Account> findByAccountStatus(AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByUserIdAndAccountTypeAndAccountStatus(
            Long userId, AccountType accountType,
            AccountStatus accountStatus);
}