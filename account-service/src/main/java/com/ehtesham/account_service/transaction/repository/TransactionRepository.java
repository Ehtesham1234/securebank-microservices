package com.ehtesham.account_service.transaction.repository;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.transaction.entity.Transaction;
import com.ehtesham.account_service.transaction.enums.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByAccount(Account account, Pageable pageable);

    boolean existsByTransactionRef(String transactionRef);

    Optional<Transaction> findByTransactionRef(String transactionRef);

    // M6 fix: reverseTransaction() used a plain findById() here, so two
    // concurrent reversal requests for the same transaction could both
    // read status=SUCCESS before either had written status=REVERSED,
    // and both would proceed to reverse it — double-crediting or
    // double-debiting the account. SELECT ... FOR UPDATE makes the second
    // request block until the first transaction commits, then it sees the
    // now-REVERSED status and correctly rejects instead of racing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    // M6 fix: same reasoning, for the paired leg of a TRANSFER reversal
    // (reverseTransferPair() looks this up before reversing it).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.transactionRef = :ref")
    Optional<Transaction> findByTransactionRefForUpdate(@Param("ref") String ref);

    // M8 fix: backs the daily-velocity check — how much has this account
    // already moved (of a given type) since midnight.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.account.id = :accountId AND t.type = :type " +
            "AND t.status = 'SUCCESS' AND t.createdAt >= :startOfDay")
    BigDecimal sumAmountByAccountAndTypeSince(
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type,
            @Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT t FROM Transaction t WHERE (:userId IS NULL OR t.account.userId = :userId)")
    Page<Transaction> findAllForAdmin(@Param("userId") Long userId, Pageable pageable);
}