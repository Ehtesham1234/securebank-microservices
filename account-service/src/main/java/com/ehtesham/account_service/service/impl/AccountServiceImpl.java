package com.ehtesham.account_service.service.impl;


import com.ehtesham.account_service.dto.*;
import com.ehtesham.account_service.entity.Account;
import com.ehtesham.account_service.entity.FixedDepositDetails;
import com.ehtesham.account_service.enums.AccountStatus;
import com.ehtesham.account_service.enums.AccountType;
import com.ehtesham.account_service.exception.AccountNotFoundException;
import com.ehtesham.account_service.exception.AccountOperationException;
import com.ehtesham.account_service.outbox.OutboxEvent;
import com.ehtesham.account_service.outbox.OutboxRepository;
import com.ehtesham.account_service.repository.AccountRepository;
import com.ehtesham.account_service.repository.FixedDepositDetailsRepository;
import com.ehtesham.account_service.service.AccountService;
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
import java.util.Random;
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

    public AccountServiceImpl(
            AccountRepository accountRepository,
            FixedDepositDetailsRepository fdRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.fdRepository = fdRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AccountResponse createSavingsAccount(Long userId,
                                                String firstName, String lastName) {

        if (accountRepository.existsByUserIdAndAccountType(
                userId, AccountType.SAVINGS)) {
            throw new AccountOperationException(
                    "User already has a SAVINGS account");
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

        if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            account.setBalance(request.getInitialDeposit());
        } else {
            account.setBalance(BigDecimal.ZERO);
        }

        Account saved = accountRepository.save(account);

        if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            createFixedDepositDetails(saved,
                    request.getInitialDeposit(),
                    request.getDurationMonths());
        }

        return mapToResponse(saved);
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
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll()
                .stream()
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

        AccountCreditedEvent event;

        try {
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

            event = AccountCreditedEvent.builder()
                    .loanId(loanId)
                    .accountId(accountId)
                    .newBalance(newBalance)
                    .transactionRef("LOAN-DISB-" + loanRef)
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

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = (long)(Math.random() * 9_000_000_000L)
                    + 1_000_000_000L;
            accountNumber = "SB" + number;
        } while (accountRepository
                .existsByAccountNumber(accountNumber));
        return accountNumber;
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