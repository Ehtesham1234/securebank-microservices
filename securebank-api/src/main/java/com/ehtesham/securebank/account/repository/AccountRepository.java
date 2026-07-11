package com.ehtesham.securebank.account.repository;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.common.enums.AccountStatus;
import com.ehtesham.securebank.common.enums.AccountType;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    List<Account> findByUser(User user);

    List<Account> findByUserAndAccountStatus(
            User user, AccountStatus status);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByUserAndAccountType(
            User user, AccountType accountType);

    // for admin — all accounts with specific status
    List<Account> findByAccountStatus(AccountStatus status);

    // check account number uniqueness
    boolean existsByAccountNumber(String accountNumber);
    Optional<Account> findByUserAndAccountTypeAndAccountStatus(
            User user, AccountType accountType, AccountStatus accountStatus);
}