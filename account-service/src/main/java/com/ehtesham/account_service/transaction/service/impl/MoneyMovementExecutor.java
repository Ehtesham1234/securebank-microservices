package com.ehtesham.account_service.transaction.service.impl;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.enums.AccountType;
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
import com.ehtesham.account_service.transaction.dto.EmiDebitResponse;
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
        validateNotFixedDeposit(account);
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
        validateNotFixedDeposit(account);
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
        validateNotFixedDeposit(fromAccount);
        validateNotFixedDeposit(toAccount);
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

        // Bug fix: saves used to always happen in "from, then to" order
        // — i.e. whichever account the request named first. Two
        // transfers running in opposite directions between the SAME two
        // accounts at the same moment (A→B and B→A) would then acquire
        // their row-level locks in opposite order, which is a classic
        // recipe for a DB-level deadlock (not data corruption — one side
        // just aborts and can retry — but avoidable). Saving in a fixed
        // canonical order (lower account ID first) regardless of
        // transfer direction means every concurrent transfer between
        // this pair of accounts requests their locks in the same order.
        if (fromAccount.getId() < toAccount.getId()) {
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
        } else {
            accountRepository.save(toAccount);
            accountRepository.save(fromAccount);
        }

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

    // C4 fix: previously, loan-service's payEmi() updated the loan's own
    // records (outstandingAmount, EMI status, could even close the loan)
    // but never called anything to actually move money — any customer
    // could pay off/close a loan for free with zero funds ever leaving
    // the account. This is the debit side loan-service now calls
    // synchronously, BEFORE it changes any of its own state, so a failed
    // debit here means nothing on the loan side gets modified either
    // (see LoanServiceImpl.payEmi).
    //
    // Idempotent the same way disbursement now is (see doCreditForLoan
    // below): transactionRef is deterministic ("EMI-<loanId>-<emiNumber>"),
    // and transaction_ref has a real DB-level UNIQUE constraint. The
    // findByTransactionRef check below is the fast path for a simple
    // retry after the first call already succeeded. If two calls for the
    // exact same EMI genuinely race each other, the loser's INSERT hits
    // the unique constraint, this whole @Transactional method rolls back
    // (safely undoing that request's balance debit — it never partially
    // applies), and the caller gets a 409 rather than a silent double
    // debit. That's an acceptable trade-off for a service-to-service,
    // retry-friendly call — not the same bar as a browser-facing button.
    @Transactional
    public EmiDebitResponse doEmiDebit(
            Long accountId, Long userId, Long loanId, Integer emiNumber,
            BigDecimal amount, String description) {

        String ref = "EMI-" + loanId + "-" + emiNumber;

        Transaction existing = transactionRepository
                .findByTransactionRef(ref)
                .orElse(null);

        if (existing != null) {
            return EmiDebitResponse.builder()
                    .success(true)
                    .newBalance(existing.getBalanceAfter())
                    .transactionRef(existing.getTransactionRef())
                    .build();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));

        // Bug fix: this method used to load the account with no
        // ownership check at all, unlike every other money-movement
        // method here (which all go through getOwnedAccount). The
        // controller's @PreAuthorize only verifies that `userId` matches
        // the caller — it never verified that `accountId` belongs to
        // that userId, so this method alone decided whose money actually
        // moved. It was only safe in practice because loan-service
        // separately validates accountId == loan.getAccountId() before
        // calling, and the gateway has no route to /api/v1/internal/**.
        // Enforcing it here too means this endpoint is safe on its own
        // terms, not just because of what currently calls it.
        if (!account.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                    "Account not found: " + accountId);
        }

        validateAccountActive(account);
        validateNotFixedDeposit(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for EMI payment");
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(ref);
        transaction.setAccount(account);
        transaction.setType(TransactionType.WITHDRAW);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(description);
        transactionRepository.save(transaction);

        eventPublisher.publishTransactionCompleted(
                account.getUserId(),
                account.getAccountNumber(),
                newBalance,
                amount,
                TransactionType.WITHDRAW,
                ref,
                description);

        return EmiDebitResponse.builder()
                .success(true)
                .newBalance(newBalance)
                .transactionRef(ref)
                .build();
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

    // C8 fix: a Fixed Deposit is a locked lump-sum instrument — it's
    // funded once at opening (applyForFixedDeposit(), which now debits a
    // real source account for it) and paid out once at maturity
    // (processMaturedFixedDeposits()). Before this check existed, none
    // of deposit/withdraw/transfer distinguished an FD account from a
    // regular one at all, so an FD's balance (previously mintable out of
    // nowhere — see applyForFixedDeposit()) could just be withdrawn or
    // transferred out immediately like ordinary funds.
    private void validateNotFixedDeposit(Account account) {
        if (account.getAccountType() == AccountType.FIXED_DEPOSIT) {
            throw new AccountOperationException(
                    "Fixed Deposit accounts are locked until maturity " +
                            "and can't be used for deposits, " +
                            "withdrawals, or transfers.");
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
