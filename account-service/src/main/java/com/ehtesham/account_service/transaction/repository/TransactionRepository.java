package com.ehtesham.account_service.transaction.repository;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByAccount(Account account, Pageable pageable);

    boolean existsByTransactionRef(String transactionRef);

    Optional<Transaction> findByTransactionRef(String transactionRef);
}