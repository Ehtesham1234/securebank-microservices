package com.ehtesham.securebank.account.service.impl;

import com.ehtesham.securebank.account.dto.AccountApplicationRequest;
import com.ehtesham.securebank.account.dto.AccountResponse;
import com.ehtesham.securebank.account.dto.FixedDepositResponse;
import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.entity.FixedDepositDetails;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.repository.FixedDepositDetailsRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.audit.annotation.Auditable;
import com.ehtesham.securebank.common.enums.AccountStatus;
import com.ehtesham.securebank.common.enums.AccountType;
import com.ehtesham.securebank.common.exception.AccountOperationException;
import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    private final AccountRepository accountRepository;
    private final FixedDepositDetailsRepository fdRepository;
    private final UserRepository userRepository;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            FixedDepositDetailsRepository fdRepository,
            UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.fdRepository = fdRepository;
        this.userRepository = userRepository;
    }
    @Override
    public Account getOwnedAccount(Long accountId, User user) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Account not found");
        }

        return account;
    }

    @Override
    @Transactional
    public AccountResponse createSavingsAccount(User user) {

        if (accountRepository.existsByUserAndAccountType(
                user, AccountType.SAVINGS)) {
            throw new AccountOperationException(
                    "User already has a SAVINGS account");
        }
        Account account = new Account();
        account.setUser(user);
        account.setAccountType(AccountType.SAVINGS);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        account.setAccountNumber(generateAccountNumber());

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse applyForAccount(
            AccountApplicationRequest request,
            String email) {

        User user = getUser(email);

        // SAVINGS — only one allowed per customer
        if (request.getAccountType() == AccountType.SAVINGS) {
            if (accountRepository.existsByUserAndAccountType(
                    user, AccountType.SAVINGS)) {
                throw new AccountOperationException(
                        "You already have a SAVINGS account");
            }
        }

        // FIXED_DEPOSIT — validate required fields
        if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            if (request.getInitialDeposit() == null
                    || request.getDurationMonths() == null) {
                throw new IllegalArgumentException(
                        "Initial deposit and duration are required " +
                                "for Fixed Deposit");
            }
        }

        Account account = new Account();
        account.setUser(user);
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountNumber(generateAccountNumber());

        // set initial balance
        account.setBalance(
                request.getInitialDeposit() != null
                        ? request.getInitialDeposit()
                        : BigDecimal.ZERO
        );

        Account saved = accountRepository.save(account);

        // create FD details if FIXED_DEPOSIT
        if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            createFixedDepositDetails(
                    saved,
                    request.getInitialDeposit(),
                    request.getDurationMonths());
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String email) {

        User user = getUser(email);

        return accountRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id, String email) {

        User user = getUser(email);
        Account account = getOwnedAccount(id, user);

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
    @Auditable(action = "ACCOUNT_FREEZE")
    @Override
    @Transactional
    public AccountResponse freezeAccount(Long id) {

        Account account = getAccount(id);

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

        Account account = getAccount(id);

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

        Account account = getAccount(id);

        if (account.getBalance()
                .compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountOperationException(
                    "Cannot close account with remaining balance. " +
                            "Please withdraw all funds first.");
        }

        account.setAccountStatus(AccountStatus.CLOSED);
        return mapToResponse(accountRepository.save(account));
    }

    // ── Private helpers ──────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"));
    }

    private Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found"));
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            // SB + 10 random digits
            long number = (long) (Math.random() * 9_000_000_000L)
                    + 1_000_000_000L;
            accountNumber = "SB" + number;
        } while (accountRepository
                .existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private void createFixedDepositDetails(
            Account account,
            BigDecimal principal,
            int durationMonths) {

        // interest rate based on duration
        BigDecimal interestRate = getInterestRate(durationMonths);

        // simple interest: A = P(1 + rt)
        // r = annual rate, t = time in years
        BigDecimal timeInYears = BigDecimal.valueOf(durationMonths)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal maturityAmount = principal.multiply(
                BigDecimal.ONE.add(
                        interestRate.divide(
                                        BigDecimal.valueOf(100), 10,
                                        RoundingMode.HALF_UP)
                                .multiply(timeInYears)
                )
        ).setScale(4, RoundingMode.HALF_UP);

        FixedDepositDetails fd = new FixedDepositDetails();
        fd.setAccount(account);
        fd.setPrincipalAmount(principal);
        fd.setInterestRate(interestRate);
        fd.setDurationMonths(durationMonths);
        fd.setMaturityDate(
                LocalDate.now().plusMonths(durationMonths));
        fd.setMaturityAmount(maturityAmount);

        fdRepository.save(fd);
    }

    private BigDecimal getInterestRate(int durationMonths) {
        // real bank rates based on duration
        if (durationMonths <= 3)  return new BigDecimal("4.00");
        if (durationMonths <= 6)  return new BigDecimal("5.50");
        if (durationMonths <= 12) return new BigDecimal("6.50");
        if (durationMonths <= 24) return new BigDecimal("7.00");
        if (durationMonths <= 36) return new BigDecimal("7.25");
        return new BigDecimal("7.50"); // 36+ months
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
                .ownerEmail(account.getUser().getEmail())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .fixedDepositDetails(fdResponse)
                .createdAt(account.getCreatedAt())
                .build();
    }
}