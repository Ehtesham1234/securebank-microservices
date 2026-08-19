package com.ehtesham.account_service.account.service.impl;


import com.ehtesham.account_service.account.dto.*;
import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.entity.FixedDepositDetails;
import com.ehtesham.account_service.account.enums.AccountStatus;
import com.ehtesham.account_service.account.enums.AccountType;
import com.ehtesham.account_service.account.enums.FdStatus;
import com.ehtesham.account_service.exception.AccountNotFoundException;
import com.ehtesham.account_service.exception.AccountOperationException;
import com.ehtesham.account_service.exception.InsufficientFundsException;
import com.ehtesham.account_service.account.outbox.OutboxEvent;
import com.ehtesham.account_service.account.outbox.OutboxRepository;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.repository.FixedDepositDetailsRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.transaction.entity.Transaction;
import com.ehtesham.account_service.transaction.enums.TransactionStatus;
import com.ehtesham.account_service.transaction.enums.TransactionType;
import com.ehtesham.account_service.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log =
            LoggerFactory.getLogger(AccountServiceImpl.class);

    private static final String TOPIC_ACCOUNT_EVENTS =
            "account-events";

    private final AccountRepository accountRepository;
    private final FixedDepositDetailsRepository fdRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;

    // Bug fix: self-injection (via a @Lazy proxy) so
    // processMaturedFixedDeposits() can call payOutMaturedFixedDeposit()
    // THROUGH Spring's proxy — needed for its
    // @Transactional(REQUIRES_NEW) to actually take effect, the same
    // pattern RefreshTokenServiceImpl uses for revokeTokenFamily() and
    // CardServiceImpl now uses for generateStatementForCard().
    private final AccountService self;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            FixedDepositDetailsRepository fdRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            TransactionRepository transactionRepository,
            @org.springframework.context.annotation.Lazy AccountService self) {
        this.accountRepository = accountRepository;
        this.fdRepository = fdRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.transactionRepository = transactionRepository;
        this.self = self;
    }

    @Override
    @Transactional
    public AccountResponse createSavingsAccount(Long userId,
                                                String firstName, String lastName) {

        // This handles Feign retries gracefully
        Optional<Account> existing = accountRepository
                .findByUserIdAndAccountTypeAndAccountStatus(
                        userId,
                        AccountType.SAVINGS,
                        AccountStatus.ACTIVE);

        if (existing.isPresent()) {
            log.info("Savings account already exists for " +
                            "userId={}, returning existing: {}",
                    userId,
                    existing.get().getAccountNumber());
            return mapToResponse(existing.get());
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setUserId(userId);
        account.setAccountType(AccountType.SAVINGS);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);

        return mapToResponse(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountResponse applyForAccount(
            AccountApplicationRequest request, Long userId) {

        if (request.getAccountType() == AccountType.SAVINGS
                && accountRepository
                .existsByUserIdAndAccountType(
                        userId, AccountType.SAVINGS)) {
            throw new AccountOperationException(
                    "You already have a SAVINGS account");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setUserId(userId);
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);

        // C8 fix: this used to do `account.setBalance(initialDeposit)`
        // directly — materializing a balance with nothing debited from
        // anywhere, no cap on the amount, and no restriction on how many
        // FDs one customer could open. A Fixed Deposit has to be FUNDED
        // from a real account the customer already owns, same as any
        // other transfer.
        if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            return applyForFixedDeposit(account, request, userId);
        }

        return mapToResponse(accountRepository.save(account));
    }

    // C8 fix: split out of applyForAccount(). Debits the customer's
    // SAVINGS account for the principal — same account payCreditCardBill()
    // already treats as "the" funding source — before the FD account is
    // ever considered real. If the debit can't happen, nothing here gets
    // persisted at all (this is still inside applyForAccount()'s
    // @Transactional boundary).
    private AccountResponse applyForFixedDeposit(
            Account fdAccount, AccountApplicationRequest request,
            Long userId) {

        BigDecimal principal = request.getInitialDeposit();

        Account fundingAccount = accountRepository
                .findByUserIdAndAccountTypeAndAccountStatus(
                        userId, AccountType.SAVINGS, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AccountOperationException(
                        "An active SAVINGS account is required to " +
                                "fund a Fixed Deposit."));

        if (fundingAccount.getBalance().compareTo(principal) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance in your SAVINGS account to " +
                            "fund this Fixed Deposit. Available: ₹"
                            + fundingAccount.getBalance().toPlainString());
        }

        BigDecimal fundingNewBalance =
                fundingAccount.getBalance().subtract(principal);
        fundingAccount.setBalance(fundingNewBalance);
        accountRepository.save(fundingAccount);

        fdAccount.setBalance(principal);
        Account savedFd = accountRepository.save(fdAccount);

        // Ledger entries on both sides — same TRANSFER_OUT/TRANSFER_IN
        // convention MoneyMovementExecutor.doTransfer() uses for an
        // ordinary internal transfer, because that's functionally what
        // this is.
        String sharedRef = generateTransactionRef();

        Transaction debit = new Transaction();
        debit.setTransactionRef(sharedRef + "-OUT");
        debit.setAccount(fundingAccount);
        debit.setType(TransactionType.TRANSFER_OUT);
        debit.setAmount(principal);
        debit.setBalanceAfter(fundingNewBalance);
        debit.setStatus(TransactionStatus.SUCCESS);
        debit.setDescription("Fixed Deposit opened — "
                + savedFd.getAccountNumber());
        debit.setRelatedAccount(savedFd);
        transactionRepository.save(debit);

        Transaction credit = new Transaction();
        credit.setTransactionRef(sharedRef + "-IN");
        credit.setAccount(savedFd);
        credit.setType(TransactionType.TRANSFER_IN);
        credit.setAmount(principal);
        credit.setBalanceAfter(principal);
        credit.setStatus(TransactionStatus.SUCCESS);
        credit.setDescription("Funded from "
                + fundingAccount.getAccountNumber());
        credit.setRelatedAccount(fundingAccount);
        transactionRepository.save(credit);

        createFixedDepositDetails(savedFd, principal,
                request.getDurationMonths());

        return mapToResponse(savedFd);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(Long userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id, Long userId) {
        Account account = getOwnedAccount(id, userId);
        return mapToResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    /** userId is optional — null returns every account bank-wide, same
     *  as before; passing it scopes the list to one customer, backed by
     *  the same findByUserId the customer-facing "my accounts" endpoint
     *  already uses. */
    public List<AccountResponse> getAllAccounts(Long userId) {
        List<Account> accounts = (userId != null)
                ? accountRepository.findByUserId(userId)
                : accountRepository.findAll();
        return accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponse freezeAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found: " + id));

        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new AccountOperationException(
                    "Cannot freeze a closed account");
        }

        account.setAccountStatus(AccountStatus.FROZEN);
        return mapToResponse(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountResponse unfreezeAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found: " + id));

        if (account.getAccountStatus() != AccountStatus.FROZEN) {
            throw new AccountOperationException(
                    "Account is not frozen");
        }

        account.setAccountStatus(AccountStatus.ACTIVE);
        return mapToResponse(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountResponse closeAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found: " + id));

        if (account.getBalance()
                .compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountOperationException(
                    "Cannot close account with balance. " +
                            "Current balance: ₹"
                            + account.getBalance().toPlainString());
        }

        account.setAccountStatus(AccountStatus.CLOSED);
        return mapToResponse(accountRepository.save(account));
    }

    @Override
    public Account getOwnedAccount(Long accountId, Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new AccountNotFoundException("Account not found");
        }

        return account;
    }

    @Override
    public AccountValidationResponse validateAccount(
            Long accountId, Long userId) {

        return accountRepository.findById(accountId)
                .map(account -> AccountValidationResponse.builder()
                        .accountId(accountId)
                        .valid(account.getUserId().equals(userId)
                                && account.getAccountStatus()
                                == AccountStatus.ACTIVE)
                        .accountNumber(account.getAccountNumber())
                        .currentBalance(account.getBalance())
                        .reason(account.getUserId().equals(userId)
                                ? null
                                : "Account does not belong to user")
                        .build())
                .orElse(AccountValidationResponse.builder()
                        .accountId(accountId)
                        .valid(false)
                        .reason("Account not found")
                        .build());
    }

    @Override
    @Transactional
    public void processCreditForLoan(Long loanId,
                                     Long accountId, BigDecimal amount, String loanRef) {

        log.info("Processing credit for loan={}, account={}, " +
                "amount={}", loanId, accountId, amount);

        String transactionRef = "LOAN-DISB-" + loanRef;
        AccountCreditedEvent event;

        try {
            // H2 fix: Kafka is at-least-once — a redelivered
            // LoanApprovedEvent (consumer restart before offset commit,
            // producer retry) used to double-credit the account with no
            // check at all. transactionRef is deterministic per loan, so
            // if it's already here, this loan was already disbursed —
            // re-publish the same success event (so a redelivery still
            // completes the saga on loan-service's side) without
            // crediting again.
            Optional<Transaction> existing =
                    transactionRepository.findByTransactionRef(transactionRef);

            if (existing.isPresent()) {
                log.warn("Loan {} already disbursed (ref={}) — skipping " +
                        "duplicate credit, likely a redelivered event",
                        loanId, transactionRef);

                event = AccountCreditedEvent.builder()
                        .loanId(loanId)
                        .accountId(accountId)
                        .newBalance(existing.get().getBalanceAfter())
                        .transactionRef(transactionRef)
                        .success(true)
                        .build();

                saveToOutbox(event, loanId.toString());
                return;
            }

            Account account = accountRepository
                    .findById(accountId)
                    .orElseThrow(() ->
                            new AccountNotFoundException(
                                    "Account not found: " + accountId));

            if (account.getAccountStatus() != AccountStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Account is not active: " + accountId);
            }

            BigDecimal newBalance =
                    account.getBalance().add(amount);
            account.setBalance(newBalance);
            accountRepository.save(account);

            // H2 fix: this used to never write a Transaction row at
            // all — loan disbursements were invisible in transaction
            // history, and transactionRef had been computed but never
            // actually used for anything. Writing it here closes both
            // gaps: it's now a real, visible ledger entry, and its
            // uniqueness (transaction_ref has a DB-level UNIQUE
            // constraint) is what makes the idempotency check above safe
            // even under a genuine race, not just a sequential redelivery.
            Transaction transaction = new Transaction();
            transaction.setTransactionRef(transactionRef);
            transaction.setAccount(account);
            transaction.setType(TransactionType.DEPOSIT);
            transaction.setAmount(amount);
            transaction.setBalanceAfter(newBalance);
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setDescription("Loan disbursement — " + loanRef);
            transactionRepository.save(transaction);

            event = AccountCreditedEvent.builder()
                    .loanId(loanId)
                    .accountId(accountId)
                    .newBalance(newBalance)
                    .transactionRef(transactionRef)
                    .success(true)
                    .build();

            log.info("Account {} credited ₹{}, new balance: {}",
                    accountId, amount, newBalance);

        } catch (Exception e) {
            log.error("Failed to credit account {} for loan {}: {}",
                    accountId, loanId, e.getMessage());

            event = AccountCreditedEvent.builder()
                    .loanId(loanId)
                    .accountId(accountId)
                    .success(false)
                    .failureReason(e.getMessage())
                    .build();
        }

        saveToOutbox(event, loanId.toString());
    }

    // C9 fix: previously nothing anywhere ever paid an FD out — no
    // status field, no scheduled job, no manual endpoint. This finds
    // every ACTIVE FD whose maturityDate has arrived, credits the
    // customer's SAVINGS account with the full maturityAmount, empties
    // and closes the FD account, and marks the FD MATURED so it's never
    // reprocessed.
    //
    // Idempotent the same way loan disbursement is: transactionRef is
    // deterministic ("FD-MATURITY-<fdId>"), checked before doing
    // anything else, so a scheduler run that overlaps a previous one (or
    // retries after a partial failure) can't double-pay the same FD.
    // Each FD is handled in its own try/catch so one bad row (e.g. a
    // customer whose SAVINGS account got frozen after the FD was opened)
    // doesn't stop the rest of the batch from being paid out.
    @Override
    @Transactional(readOnly = true)
    public void processMaturedFixedDeposits() {

        // Bug fix: this used to load every due FD and process them all
        // inside ONE @Transactional method/one Hibernate session. The
        // per-FD try/catch looked like it isolated failures, but it
        // didn't: Hibernate defers writes until a flush, and a flush
        // triggered by a LATER FD's query (payOutMaturedFixedDeposit
        // starts with a findByTransactionRef lookup, which forces a
        // flush of whatever's pending from the previous FD) — or by the
        // final commit after the loop — can fail outside any per-FD
        // try/catch and roll back the WHOLE transaction, silently
        // undoing every payout that appeared to succeed earlier in the
        // same run. Only IDs are collected here (read-only); each FD is
        // now processed through the self-proxy so
        // @Transactional(REQUIRES_NEW) actually takes effect and a bad
        // FD can only ever roll back that one FD's payout.
        List<Long> dueFdIds = fdRepository
                .findByStatusAndMaturityDateLessThanEqual(
                        FdStatus.ACTIVE, LocalDate.now())
                .stream()
                .map(FixedDepositDetails::getId)
                .collect(Collectors.toList());

        int succeeded = 0;
        for (Long fdId : dueFdIds) {
            try {
                self.payOutMaturedFixedDeposit(fdId);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to process maturity for FD {}: {}",
                        fdId, e.getMessage(), e);
                // Left ACTIVE — picked up again on the next run.
            }
        }

        log.info("FD maturity processing: {}/{} FDs succeeded",
                succeeded, dueFdIds.size());
    }

    @Override
    @Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void payOutMaturedFixedDeposit(Long fdId) {

        FixedDepositDetails fd = fdRepository.findById(fdId)
                .orElseThrow(() -> new AccountOperationException(
                        "Fixed deposit not found: " + fdId));

        String transactionRef = "FD-MATURITY-" + fd.getId();

        if (transactionRepository.findByTransactionRef(transactionRef)
                .isPresent()) {
            log.warn("FD {} already paid out (ref={}) — marking " +
                    "MATURED without paying again, likely an " +
                    "overlapping scheduler run", fd.getId(),
                    transactionRef);
            fd.setStatus(FdStatus.MATURED);
            fdRepository.save(fd);
            return;
        }

        Account fdAccount = fd.getAccount();

        Account savingsAccount = accountRepository
                .findByUserIdAndAccountTypeAndAccountStatus(
                        fdAccount.getUserId(), AccountType.SAVINGS,
                        AccountStatus.ACTIVE)
                .orElseThrow(() -> new AccountOperationException(
                        "No active SAVINGS account to pay FD " +
                                fd.getId() + " out to"));

        BigDecimal payout = fd.getMaturityAmount();
        BigDecimal savingsNewBalance =
                savingsAccount.getBalance().add(payout);
        savingsAccount.setBalance(savingsNewBalance);
        accountRepository.save(savingsAccount);

        fdAccount.setBalance(BigDecimal.ZERO);
        fdAccount.setAccountStatus(AccountStatus.CLOSED);
        accountRepository.save(fdAccount);

        Transaction debit = new Transaction();
        debit.setTransactionRef(transactionRef + "-OUT");
        debit.setAccount(fdAccount);
        debit.setType(TransactionType.TRANSFER_OUT);
        debit.setAmount(payout);
        debit.setBalanceAfter(BigDecimal.ZERO);
        debit.setStatus(TransactionStatus.SUCCESS);
        debit.setDescription("Fixed Deposit matured — paid out to "
                + savingsAccount.getAccountNumber());
        debit.setRelatedAccount(savingsAccount);
        transactionRepository.save(debit);

        Transaction credit = new Transaction();
        credit.setTransactionRef(transactionRef + "-IN");
        credit.setAccount(savingsAccount);
        credit.setType(TransactionType.TRANSFER_IN);
        credit.setAmount(payout);
        credit.setBalanceAfter(savingsNewBalance);
        credit.setStatus(TransactionStatus.SUCCESS);
        credit.setDescription("Fixed Deposit " + fdAccount.getAccountNumber()
                + " matured (principal ₹" + fd.getPrincipalAmount()
                + " + interest)");
        credit.setRelatedAccount(fdAccount);
        transactionRepository.save(credit);

        fd.setStatus(FdStatus.MATURED);
        fdRepository.save(fd);

        log.info("FD {} matured: ₹{} paid out from account {} to " +
                "savings account {}", fd.getId(), payout,
                fdAccount.getAccountNumber(),
                savingsAccount.getAccountNumber());
    }

    // ── Private helpers ───────────────────────────────────────────

    private void saveToOutbox(AccountCreditedEvent event,
                              String aggregateId) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTopic(TOPIC_ACCOUNT_EVENTS);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(event.isSuccess()
                    ? "ACCOUNT_CREDITED"
                    : "ACCOUNT_CREDIT_FAILED");
            outboxEvent.setPayload(
                    objectMapper.writeValueAsString(event));
            outboxEvent.setPublished(false);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize outbox event", e);
        }
    }

    private void createFixedDepositDetails(Account account,
                                           BigDecimal principal, int durationMonths) {

        BigDecimal interestRate = getInterestRate(durationMonths);
        BigDecimal timeInYears = BigDecimal.valueOf(durationMonths)
                .divide(BigDecimal.valueOf(12), 10,
                        RoundingMode.HALF_UP);
        BigDecimal maturityAmount = principal.multiply(
                        BigDecimal.ONE.add(
                                interestRate
                                        .divide(BigDecimal.valueOf(100),
                                                10, RoundingMode.HALF_UP)
                                        .multiply(timeInYears)))
                .setScale(4, RoundingMode.HALF_UP);

        FixedDepositDetails fd = new FixedDepositDetails();
        fd.setAccount(account);
        fd.setPrincipalAmount(principal);
        fd.setInterestRate(interestRate);
        fd.setDurationMonths(durationMonths);
        fd.setMaturityDate(LocalDate.now()
                .plusMonths(durationMonths));
        fd.setMaturityAmount(maturityAmount);

        fdRepository.save(fd);
    }

    private BigDecimal getInterestRate(int durationMonths) {
        if (durationMonths <= 6)
            return new BigDecimal("5.50");
        if (durationMonths <= 12)
            return new BigDecimal("6.50");
        if (durationMonths <= 24)
            return new BigDecimal("7.00");
        return new BigDecimal("7.25");
    }

    // Bug fix: java.util.Random-backed Math.random() is a predictable
    // PRNG — CardServiceImpl already made this switch for card numbers
    // (see its SECURE_RANDOM field/H2 fix); account numbers deserved the
    // same consistency even though they're not secret credentials like a
    // PAN, since a predictable sequence would still make enumeration of
    // valid account numbers easier than it needs to be.
    private static final java.security.SecureRandom ACCOUNT_NUMBER_RANDOM =
            new java.security.SecureRandom();

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = (long) (ACCOUNT_NUMBER_RANDOM.nextDouble() * 9_000_000_000L)
                    + 1_000_000_000L;
            accountNumber = "SB" + number;
        } while (accountRepository
                .existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    // C8 fix: applyForFixedDeposit() writes real ledger entries now, so
    // it needs the same kind of unique ref MoneyMovementExecutor already
    // generates for its own transfers.
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

    private AccountResponse mapToResponse(Account account) {
        FixedDepositResponse fdResponse = null;
        if (account.getAccountType() == AccountType.FIXED_DEPOSIT) {
            fdResponse = fdRepository
                    .findByAccount(account)
                    .map(fd -> FixedDepositResponse.builder()
                            .principalAmount(fd.getPrincipalAmount())
                            .interestRate(fd.getInterestRate())
                            .durationMonths(fd.getDurationMonths())
                            .maturityDate(fd.getMaturityDate())
                            .maturityAmount(fd.getMaturityAmount())
                            .build())
                    .orElse(null);
        }

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .userId(account.getUserId())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .fixedDepositDetails(fdResponse)
                .createdAt(account.getCreatedAt())
                .build();
    }
}