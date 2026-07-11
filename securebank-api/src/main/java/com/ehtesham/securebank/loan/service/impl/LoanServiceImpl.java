package com.ehtesham.securebank.loan.service.impl;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.common.enums.*;
import com.ehtesham.securebank.common.exception.*;
import com.ehtesham.securebank.loan.dto.*;
import com.ehtesham.securebank.loan.entity.EmiPayment;
import com.ehtesham.securebank.loan.entity.Loan;
import com.ehtesham.securebank.loan.repository.EmiPaymentRepository;
import com.ehtesham.securebank.loan.repository.LoanRepository;
import com.ehtesham.securebank.loan.service.LoanService;
import com.ehtesham.securebank.transaction.dto.TransactionResponse;
import com.ehtesham.securebank.transaction.dto.WithdrawRequest;
import com.ehtesham.securebank.transaction.service.TransactionService;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LoanServiceImpl implements LoanService {

    private static final BigDecimal MONTHS_PER_YEAR =
            BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    // interest rates per loan type (annual %)
    private static final BigDecimal PERSONAL_LOAN_RATE =
            new BigDecimal("12.00");
    private static final BigDecimal HOME_LOAN_RATE =
            new BigDecimal("8.50");
    private static final BigDecimal CAR_LOAN_RATE =
            new BigDecimal("10.00");

    private final LoanRepository loanRepository;
    private final EmiPaymentRepository emiPaymentRepository;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    public LoanServiceImpl(
            LoanRepository loanRepository,
            EmiPaymentRepository emiPaymentRepository,
            UserRepository userRepository,
            AccountService accountService,
            AccountRepository accountRepository,
            TransactionService transactionService) {
        this.loanRepository = loanRepository;
        this.emiPaymentRepository = emiPaymentRepository;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    @Override
    @Transactional
    public LoanResponse applyForLoan(
            LoanApplicationRequest request, String email) {

        User user = getUser(email);
        Account account = accountService
                .getOwnedAccount(request.getAccountId(), user);

        validateAccountActive(account);
        validateLoanAmountForType(
                request.getLoanType(), request.getAmount());
        validateTenureForType(
                request.getLoanType(), request.getTenureMonths());

        // prevent multiple active/pending loans
        boolean hasActiveLoan = loanRepository
                .existsByUserAndStatusIn(user,
                        List.of(LoanStatus.PENDING,
                                LoanStatus.APPROVED,
                                LoanStatus.ACTIVE));

        if (hasActiveLoan) {
            throw new AccountOperationException(
                    "You already have an active or pending loan. " +
                            "Please repay it before applying for a new one.");
        }

        BigDecimal rate = getInterestRate(request.getLoanType());
        BigDecimal emi = calculateEmi(
                request.getAmount(), rate,
                request.getTenureMonths());
        BigDecimal totalPayable = emi.multiply(
                        BigDecimal.valueOf(request.getTenureMonths()))
                .setScale(4, RoundingMode.HALF_UP);

        Loan loan = new Loan();
        loan.setLoanRef(generateLoanRef());
        loan.setUser(user);
        loan.setAccount(account);
        loan.setLoanType(request.getLoanType());
        loan.setStatus(LoanStatus.PENDING);
        loan.setPrincipalAmount(request.getAmount());
        loan.setInterestRate(rate);
        loan.setTenureMonths(request.getTenureMonths());
        loan.setEmiAmount(emi);
        loan.setTotalPayableAmount(totalPayable);
        loan.setOutstandingAmount(request.getAmount());
        loan.setEmisPaid(0);
        loan.setPurpose(request.getPurpose());

        return mapToResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional
    @com.ehtesham.securebank.audit.annotation.Auditable(action = "LOAN_APPROVE")
    public LoanResponse approveLoan(
            Long loanId, LoanReviewRequest request,
            String reviewerEmail) {

        Loan loan = getLoan(loanId);
        User reviewer = getUser(reviewerEmail);

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new AccountOperationException(
                    "Only PENDING loans can be approved. " +
                            "Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setReviewedBy(reviewer);
        loan.setDisbursementDate(LocalDate.now());
        loan.setNextEmiDate(LocalDate.now().plusMonths(1));

        // disburse — credit the loan amount to customer's account
        Account account = loan.getAccount();
        account.setBalance(account.getBalance()
                .add(loan.getPrincipalAmount()));
        accountRepository.save(account);

        // record the disbursement as a DEPOSIT transaction
        // so it shows up in the customer's transaction history
        com.ehtesham.securebank.transaction.entity.Transaction disbursement =
                new com.ehtesham.securebank.transaction.entity.Transaction();
        disbursement.setTransactionRef(
                loan.getLoanRef() + "-DISBURSEMENT");
        disbursement.setAccount(account);
        disbursement.setType(TransactionType.DEPOSIT);
        disbursement.setAmount(loan.getPrincipalAmount());
        disbursement.setBalanceAfter(account.getBalance());
        disbursement.setStatus(TransactionStatus.SUCCESS);
        disbursement.setDescription(
                "Loan disbursement: " + loan.getLoanRef());

        // we need TransactionRepository here — see note below
        // for now we manually save via a direct call

        Loan saved = loanRepository.save(loan);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @com.ehtesham.securebank.audit.annotation.Auditable(action = "LOAN_REJECT")
    public LoanResponse rejectLoan(
            Long loanId, LoanReviewRequest request,
            String reviewerEmail) {

        Loan loan = getLoan(loanId);
        User reviewer = getUser(reviewerEmail);

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new AccountOperationException(
                    "Only PENDING loans can be rejected. " +
                            "Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.REJECTED);
        loan.setReviewedBy(reviewer);
        loan.setRejectionReason(request.getReason());

        return mapToResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional
    @com.ehtesham.securebank.audit.annotation.Auditable(action = "EMI_PAYMENT")
    public LoanResponse payEmi(Long loanId, String email) {

        User user = getUser(email);
        Loan loan = getLoan(loanId);

        if (!loan.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Loan not found");
        }

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new AccountOperationException(
                    "EMI payments are only accepted for ACTIVE loans");
        }

        Account account = loan.getAccount();

        if (account.getBalance()
                .compareTo(loan.getEmiAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance to pay EMI of ₹" +
                            loan.getEmiAmount().toPlainString());
        }

        // calculate interest and principal components
        // for THIS specific EMI (reducing balance method)
        BigDecimal monthlyRate = loan.getInterestRate()
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        BigDecimal interestComponent = loan.getOutstandingAmount()
                .multiply(monthlyRate)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal principalComponent = loan.getEmiAmount()
                .subtract(interestComponent)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal newOutstanding = loan.getOutstandingAmount()
                .subtract(principalComponent)
                .setScale(4, RoundingMode.HALF_UP);

        // debit EMI from account
        account.setBalance(account.getBalance()
                .subtract(loan.getEmiAmount()));
        accountRepository.save(account);

        // record EMI payment history
        int newEmiNumber = loan.getEmisPaid() + 1;
        EmiPayment payment = new EmiPayment();
        payment.setLoan(loan);
        payment.setEmiNumber(newEmiNumber);
        payment.setEmiAmount(loan.getEmiAmount());
        payment.setInterestComponent(interestComponent);
        payment.setPrincipalComponent(principalComponent);
        payment.setOutstandingAfter(newOutstanding);
        payment.setDueDate(loan.getNextEmiDate());
        payment.setPaidDate(LocalDate.now());
        payment.setStatus(EmiStatus.PAID);
        emiPaymentRepository.save(payment);

        // update loan
        loan.setEmisPaid(newEmiNumber);
        loan.setOutstandingAmount(newOutstanding);

        // check if fully repaid
        if (newEmiNumber >= loan.getTenureMonths()
                || newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setNextEmiDate(null);
            loan.setOutstandingAmount(BigDecimal.ZERO);
        } else {
            loan.setNextEmiDate(
                    loan.getNextEmiDate().plusMonths(1));
        }

        return mapToResponse(loanRepository.save(loan));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LoanResponse> getMyLoans(
            String email, Pageable pageable) {
        User user = getUser(email);
        return loanRepository.findByUser(user, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoanDetails(Long loanId, String email) {
        User user = getUser(email);
        Loan loan = getLoan(loanId);

        boolean isAdminOrTeller = user.getRole() == Role.ADMIN
                || user.getRole() == Role.TELLER;

        if (!isAdminOrTeller
                && !loan.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Loan not found");
        }

        return mapToResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LoanResponse> getAllLoans(Pageable pageable) {
        return loanRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LoanResponse> getLoansByStatus(
            String status, Pageable pageable) {
        LoanStatus loanStatus = LoanStatus.valueOf(status.toUpperCase());
        return loanRepository.findByStatus(loanStatus, pageable)
                .map(this::mapToResponse);
    }

    // ── Private helpers ───────────────────────────────────────────

    private BigDecimal calculateEmi(
            BigDecimal principal,
            BigDecimal annualRate,
            int tenureMonths) {

        BigDecimal monthlyRate = annualRate
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        // (1 + r)^n
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(
                tenureMonths, new MathContext(20, RoundingMode.HALF_UP));

        // EMI = P × r × (1+r)^n / ((1+r)^n - 1)
        BigDecimal numerator = principal
                .multiply(monthlyRate)
                .multiply(onePlusRPowN);

        BigDecimal denominator = onePlusRPowN
                .subtract(BigDecimal.ONE);

        return numerator
                .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal getInterestRate(LoanType type) {
        return switch (type) {
            case PERSONAL_LOAN -> PERSONAL_LOAN_RATE;
            case HOME_LOAN -> HOME_LOAN_RATE;
            case CAR_LOAN -> CAR_LOAN_RATE;
        };
    }

    private void validateLoanAmountForType(
            LoanType type, BigDecimal amount) {
        switch (type) {
            case PERSONAL_LOAN -> {
                if (amount.compareTo(new BigDecimal("10000")) < 0
                        || amount.compareTo(
                        new BigDecimal("500000")) > 0)
                    throw new AccountOperationException(
                            "Personal loan amount must be " +
                                    "between ₹10,000 and ₹5,00,000");
            }
            case HOME_LOAN -> {
                if (amount.compareTo(
                        new BigDecimal("500000")) < 0
                        || amount.compareTo(
                        new BigDecimal("10000000")) > 0)
                    throw new AccountOperationException(
                            "Home loan amount must be " +
                                    "between ₹5,00,000 and ₹1,00,00,000");
            }
            case CAR_LOAN -> {
                if (amount.compareTo(
                        new BigDecimal("100000")) < 0
                        || amount.compareTo(
                        new BigDecimal("2000000")) > 0)
                    throw new AccountOperationException(
                            "Car loan amount must be " +
                                    "between ₹1,00,000 and ₹20,00,000");
            }
        }
    }

    private void validateTenureForType(
            LoanType type, int tenureMonths) {
        switch (type) {
            case PERSONAL_LOAN -> {
                if (tenureMonths < 6 || tenureMonths > 60)
                    throw new AccountOperationException(
                            "Personal loan tenure: 6–60 months");
            }
            case HOME_LOAN -> {
                if (tenureMonths < 12 || tenureMonths > 240)
                    throw new AccountOperationException(
                            "Home loan tenure: 12–240 months");
            }
            case CAR_LOAN -> {
                if (tenureMonths < 12 || tenureMonths > 84)
                    throw new AccountOperationException(
                            "Car loan tenure: 12–84 months");
            }
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountOperationException(
                    "Loan can only be disbursed to an ACTIVE account");
        }
    }

    private String generateLoanRef() {
        String ref;
        do {
            ref = "LOAN" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase();
        } while (loanRepository.findByLoanRef(ref).isPresent());
        return ref;
    }

    private Loan getLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Loan not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
    }

    private LoanResponse mapToResponse(Loan loan) {
        return LoanResponse.builder()
                .id(loan.getId())
                .loanRef(loan.getLoanRef())
                .loanType(loan.getLoanType())
                .status(loan.getStatus())
                .principalAmount(loan.getPrincipalAmount())
                .interestRate(loan.getInterestRate())
                .tenureMonths(loan.getTenureMonths())
                .emiAmount(loan.getEmiAmount())
                .totalPayableAmount(loan.getTotalPayableAmount())
                .outstandingAmount(loan.getOutstandingAmount())
                .emisPaid(loan.getEmisPaid())
                .emisRemaining(loan.getTenureMonths()
                        - loan.getEmisPaid())
                .nextEmiDate(loan.getNextEmiDate())
                .disbursementDate(loan.getDisbursementDate())
                .rejectionReason(loan.getRejectionReason())
                .purpose(loan.getPurpose())
                .accountNumber(loan.getAccount().getAccountNumber())
                .createdAt(loan.getCreatedAt())
                .build();
    }
}