package com.ehtesham.account_service.transaction.service.impl;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.client.InternalUserStatusResponse;
import com.ehtesham.account_service.client.UserStatusCheckUnavailableException;
import com.ehtesham.account_service.client.UserStatusClient;
import com.ehtesham.account_service.exception.AccountOperationException;
import com.ehtesham.account_service.exception.AccountSuspendedException;
import com.ehtesham.account_service.exception.InsufficientFundsException;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import com.ehtesham.account_service.transaction.dto.DepositRequest;
import com.ehtesham.account_service.transaction.dto.TransactionResponse;
import com.ehtesham.account_service.transaction.dto.TransferRequest;
import com.ehtesham.account_service.transaction.dto.WithdrawRequest;
import com.ehtesham.account_service.transaction.entity.Transaction;
import com.ehtesham.account_service.transaction.enums.TransactionStatus;
import com.ehtesham.account_service.transaction.enums.TransactionType;
import com.ehtesham.account_service.transaction.publisher.TransactionEventPublisher;
import com.ehtesham.account_service.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Follow-up fix #1 (critical): doDeposit/doWithdraw/doTransfer used to
 * live in TransactionServiceImpl as @Transactional methods, but were only
 * ever called via a this-capturing lambda from the public deposit()/
 * withdraw()/transfer() methods — a self-invocation that bypasses
 * Spring's CGLIB proxy entirely, so @Transactional was silently never
 * applied. Each individual repository .save() still committed on its
 * own, but the group of calls inside one of these methods didn't commit
 * or roll back as a unit — worst case, doTransfer() could debit the
 * sender and crash before crediting the receiver, with no rollback.
 *
 * Moving these into their own bean means TransactionServiceImpl calls
 * them as a genuine bean-to-bean call (moneyMovementExecutor.doDeposit(...)
 * instead of this.doDeposit(...)) — Spring's proxy actually sees and
 * intercepts the call, so @Transactional works as intended.
 *
 * reverseTransaction()/reverseTransferPair()/doSingleReversal() are NOT
 * moved here — they already work correctly today, because
 * reverseTransaction() itself is a genuine proxy entry point (called from
 * the controller, not self-invoked), so its @Transactional already covers
 * everything nested inside it regardless of those nested calls being
 * self-invoked private methods.
 */
@Component
public class MoneyMovementExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(MoneyMovementExecutor.class);

    // M8 fix (moved from TransactionServiceImpl): configurable daily
    // velocity caps, checked in addition to the per-transaction ceiling
    // on the request DTOs (@DecimalMax).
    @Value("${transaction.limits.daily-max-deposit:2000000.00}")
    private BigDecimal dailyMaxDeposit;

    @Value("${transaction.limits.daily-max-withdraw:1000000.00}")
    private BigDecimal dailyMaxWithdraw;

    @Value("${transaction.limits.daily-max-transfer:1000000.00}")
    private BigDecimal dailyMaxTransfer;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransactionEventPublisher eventPublisher;
    private final UserStatusClient userStatusClient;

    public MoneyMovementExecutor(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            TransactionEventPublisher eventPublisher,
            UserStatusClient userStatusClient) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
        this.userStatusClient = userStatusClient;
    }

    @Transactional
    public TransactionResponse doDeposit(
            Long accountId, DepositRequest request, Long userId) {

        Account account = accountService
                .getOwnedAccount(accountId, userId);
        validateAccountActive(account);
        verifyLiveUserStatus(userId);
        checkDailyLimit(accountId, TransactionType.DEPOSIT,
                request.getAmount(), dailyMaxDeposit);

        BigDecimal newBalance = account.getBalance()
                .add(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(generateTransactionRef());
        transaction.setAccount(account);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(request.getAmount());
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(request.getDescription());

        Transaction saved = transactionRepository.save(transaction);

        // Publish to Kafka → securebank-api → WebSocket
        eventPublisher.publishTransactionCompleted(
                userId,
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.DEPOSIT,
                saved.getTransactionRef(),
                request.getDescription());

        return mapToResponse(saved);
    }

    @Transactional
    public TransactionResponse doWithdraw(
            Long accountId, WithdrawRequest request, Long userId) {

        Account account = accountService
                .getOwnedAccount(accountId, userId);
        validateAccountActive(account);
        verifyLiveUserStatus(userId);
        checkDailyLimit(accountId, TransactionType.WITHDRAW,
                request.getAmount(), dailyMaxWithdraw);

        if (account.getBalance()
                .compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for this withdrawal");
        }

        BigDecimal newBalance = account.getBalance()
                .subtract(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(generateTransactionRef());
        transaction.setAccount(account);
        transaction.setType(TransactionType.WITHDRAW);
        transaction.setAmount(request.getAmount());
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(request.getDescription());

        Transaction saved = transactionRepository.save(transaction);

        eventPublisher.publishTransactionCompleted(
                userId,
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.WITHDRAW,
                saved.getTransactionRef(),
                request.getDescription());

        return mapToResponse(saved);
    }

    @Transactional
    public TransactionResponse doTransfer(
            TransferRequest request, Long userId) {

        if (request.getFromAccountNumber()
                .equals(request.getToAccountNumber())) {
            throw new AccountOperationException(
                    "Cannot transfer to the same account");
        }

        Account fromAccount = accountRepository
                .findByAccountNumber(
                        request.getFromAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source account not found"));

        // Verify ownership — userId must match account's userId
        if (!fromAccount.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                    "Source account not found");
        }

        Account toAccount = accountRepository
                .findByAccountNumber(
                        request.getToAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Destination account not found"));

        validateAccountActive(fromAccount);
        validateAccountActive(toAccount);
        verifyLiveUserStatus(userId);
        checkDailyLimit(fromAccount.getId(), TransactionType.TRANSFER_OUT,
                request.getAmount(), dailyMaxTransfer);

        if (fromAccount.getBalance()
                .compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for this transfer");
        }

        BigDecimal fromNewBalance = fromAccount.getBalance()
                .subtract(request.getAmount());
        BigDecimal toNewBalance = toAccount.getBalance()
                .add(request.getAmount());

        fromAccount.setBalance(fromNewBalance);
        toAccount.setBalance(toNewBalance);
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        String sharedRef = generateTransactionRef();

        Transaction outgoing = new Transaction();
        outgoing.setTransactionRef(sharedRef + "-OUT");
        outgoing.setAccount(fromAccount);
        outgoing.setType(TransactionType.TRANSFER_OUT);
        outgoing.setAmount(request.getAmount());
        outgoing.setBalanceAfter(fromNewBalance);
        outgoing.setStatus(TransactionStatus.SUCCESS);
        outgoing.setDescription(request.getDescription());
        outgoing.setRelatedAccount(toAccount);
        Transaction savedOutgoing =
                transactionRepository.save(outgoing);

        Transaction incoming = new Transaction();
        incoming.setTransactionRef(sharedRef + "-IN");
        incoming.setAccount(toAccount);
        incoming.setType(TransactionType.TRANSFER_IN);
        incoming.setAmount(request.getAmount());
        incoming.setBalanceAfter(toNewBalance);
        incoming.setStatus(TransactionStatus.SUCCESS);
        incoming.setDescription(request.getDescription());
        incoming.setRelatedAccount(fromAccount);
        transactionRepository.save(incoming);

        // Push to SENDER
        eventPublisher.publishTransactionCompleted(
                userId,
                fromAccount.getAccountNumber(),
                fromNewBalance,
                request.getAmount(),
                TransactionType.TRANSFER_OUT,
                sharedRef + "-OUT",
                request.getDescription());

        // Push to RECEIVER — use toAccount.getUserId()
        // (plain Long field on Account entity, no lazy load)
        eventPublisher.publishTransactionCompleted(
                toAccount.getUserId(),
                toAccount.getAccountNumber(),
                toNewBalance,
                request.getAmount(),
                TransactionType.TRANSFER_IN,
                sharedRef + "-IN",
                request.getDescription());

        return mapToResponse(savedOutgoing);
    }

    // M5 fix: X-User-Status (and therefore SecurityContext/gateway
    // enforcement) reflects whatever the user's status was when their JWT
    // was issued — up to jwt.expiration (15 minutes by default) stale.
    // Trade-off, made explicit: if securebank-api can't be reached, this
    // FAILS OPEN — falls back to trusting the already-verified gateway
    // status rather than blocking every deposit/withdrawal/transfer
    // bank-wide on one dependency's availability.
    private void verifyLiveUserStatus(Long userId) {
        try {
            InternalUserStatusResponse response =
                    userStatusClient.getUser(userId);

            if (!"ACTIVE".equals(response.getUserStatus())) {
                throw new AccountSuspendedException(
                        "Your account status no longer permits this " +
                                "operation. Please contact support.");
            }
        } catch (UserStatusCheckUnavailableException e) {
            log.warn("Live user-status check unavailable for userId={}; " +
                    "proceeding on the gateway-verified status from the " +
                    "request token instead.", userId);
        }
    }

    // M8 fix: daily-velocity control — sums today's SUCCESS transactions
    // of the given type for this account and rejects if the new one
    // would push the running total over the configured daily cap.
    private void checkDailyLimit(
            Long accountId, TransactionType type,
            BigDecimal amount, BigDecimal dailyMax) {

        LocalDate today = LocalDate.now();
        BigDecimal alreadyMovedToday =
                transactionRepository.sumAmountByAccountAndTypeSince(
                        accountId, type, today.atStartOfDay());

        BigDecimal projectedTotal = alreadyMovedToday.add(amount);

        if (projectedTotal.compareTo(dailyMax) > 0) {
            throw new AccountOperationException(String.format(
                    "This would exceed your daily %s limit of %s " +
                            "(already moved %s today).",
                    type.name().toLowerCase(), dailyMax, alreadyMovedToday));
        }
    }

    private void validateAccountActive(Account account) {
        switch (account.getAccountStatus()) {
            case FROZEN -> throw new AccountOperationException(
                    "This account is frozen. Contact support.");
            case CLOSED -> throw new AccountOperationException(
                    "This account has been closed.");
            case DORMANT -> throw new AccountOperationException(
                    "This account is dormant.");
            default -> { /* ACTIVE — allow */ }
        }
    }

    private String generateTransactionRef() {
        String ref;
        do {
            ref = "TXN" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase();
        } while (transactionRepository
                .existsByTransactionRef(ref));
        return ref;
    }

    private TransactionResponse mapToResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .accountNumber(t.getAccount()
                        .getAccountNumber())
                .type(t.getType())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus())
                .description(t.getDescription())
                .relatedAccountNumber(
                        t.getRelatedAccount() != null
                                ? t.getRelatedAccount()
                                .getAccountNumber()
                                : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
