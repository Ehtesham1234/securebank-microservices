package com.ehtesham.loan_service.service.impl;

import com.ehtesham.loan_service.client.AccountServiceClient;
import com.ehtesham.loan_service.dto.*;
import com.ehtesham.loan_service.entity.EmiPayment;
import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.EmiStatus;
import com.ehtesham.loan_service.enums.LoanStatus;
import com.ehtesham.loan_service.enums.LoanType;
import com.ehtesham.loan_service.exception.LoanNotFoundException;
import com.ehtesham.loan_service.exception.LoanOperationException;
import com.ehtesham.loan_service.outbox.OutboxEvent;
import com.ehtesham.loan_service.outbox.OutboxRepository;
import com.ehtesham.loan_service.repository.EmiPaymentRepository;
import com.ehtesham.loan_service.repository.LoanRepository;
import com.ehtesham.loan_service.service.LoanService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private static final Logger log =
            LoggerFactory.getLogger(LoanServiceImpl.class);

    private static final BigDecimal MONTHS_PER_YEAR =
            BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);
    private static final BigDecimal PERSONAL_LOAN_RATE =
            new BigDecimal("12.00");
    private static final BigDecimal HOME_LOAN_RATE =
            new BigDecimal("8.50");
    private static final BigDecimal CAR_LOAN_RATE =
            new BigDecimal("10.00");

    private static final String TOPIC_LOAN_EVENTS = "loan-events";

    private final LoanRepository loanRepository;
    private final EmiPaymentRepository emiPaymentRepository;
    private final OutboxRepository outboxRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public LoanServiceImpl(
            LoanRepository loanRepository,
            EmiPaymentRepository emiPaymentRepository,
            OutboxRepository outboxRepository,
            AccountServiceClient accountServiceClient,
            ObjectMapper objectMapper) {
        this.loanRepository = loanRepository;
        this.emiPaymentRepository = emiPaymentRepository;
        this.outboxRepository = outboxRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public LoanResponse applyForLoan(
            LoanApplicationRequest request,
            Long userId, String userEmail) {

        // Validate account via OpenFeign + Circuit Breaker
        AccountValidationResponse validation =
                accountServiceClient.validateAccount(
                        request.getAccountId(), userId);

        if (validation.isUnavailable()) {
            throw new LoanOperationException(
                    "Account validation service is temporarily " +
                            "unavailable. Please try again.");
        }

        if (!validation.isValid()) {
            throw new LoanOperationException(
                    "Account validation failed: "
                            + validation.getReason());
        }

        boolean hasActiveLoan = loanRepository
                .existsByUserIdAndStatusIn(userId,
                        List.of(LoanStatus.PENDING,
                                LoanStatus.APPROVED,
                                LoanStatus.ACTIVE));

        if (hasActiveLoan) {
            throw new LoanOperationException(
                    "You already have an active or pending loan");
        }

        validateLoanAmountForType(
                request.getLoanType(), request.getAmount());
        validateTenureForType(
                request.getLoanType(), request.getTenureMonths());

        BigDecimal rate = getInterestRate(request.getLoanType());
        BigDecimal emi = calculateEmi(request.getAmount(),
                rate, request.getTenureMonths());
        BigDecimal totalPayable = emi
                .multiply(BigDecimal.valueOf(
                        request.getTenureMonths()))
                .setScale(4, RoundingMode.HALF_UP);

        Loan loan = new Loan();
        loan.setLoanRef(generateLoanRef());
        loan.setUserId(userId);
        loan.setUserEmail(userEmail);
        loan.setAccountId(request.getAccountId());
        loan.setAccountNumber(validation.getAccountNumber());
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
    public LoanResponse approveLoan(Long loanId,
                                    LoanReviewRequest request, Long reviewerUserId) {

        Loan loan = getLoan(loanId);

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new LoanOperationException(
                    "Only PENDING loans can be approved. " +
                            "Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setReviewedByUserId(reviewerUserId);
        loanRepository.save(loan);

        // Publish LoanApprovedEvent via Outbox
        // account-service will consume this and credit the account
        publishLoanApprovedEvent(loan);

        log.info("Loan {} approved, LoanApprovedEvent published",
                loan.getLoanRef());

        return mapToResponse(loan);
    }

    @Override
    @Transactional
    public LoanResponse rejectLoan(Long loanId,
                                   LoanReviewRequest request, Long reviewerUserId) {

        Loan loan = getLoan(loanId);

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new LoanOperationException(
                    "Only PENDING loans can be rejected. " +
                            "Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.REJECTED);
        loan.setReviewedByUserId(reviewerUserId);
        loan.setRejectionReason(request.getReason());
        loanRepository.save(loan);

        // Publish rejection notification via Outbox
        publishLoanRejectedEvent(loan);

        return mapToResponse(loan);
    }

    @Override
    @Transactional
    public LoanResponse payEmi(Long loanId, Long userId,
                               Long accountId) {

        Loan loan = getLoan(loanId);

        if (!loan.getUserId().equals(userId)) {
            throw new LoanNotFoundException("Loan not found");
        }

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new LoanOperationException(
                    "EMI payments only accepted for ACTIVE loans");
        }

        // Note: actual balance deduction happens in account-service
        // We publish an event and account-service processes it
        // For simplicity in this implementation, we'll publish
        // the EmiPaymentRequestedEvent and update loan status

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

        loan.setEmisPaid(newEmiNumber);
        loan.setOutstandingAmount(newOutstanding);

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
            Long userId, Pageable pageable) {
        return loanRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoanDetails(Long loanId, Long userId,
                                       boolean isStaff) {
        Loan loan = getLoan(loanId);

        if (!isStaff && !loan.getUserId().equals(userId)) {
            throw new LoanNotFoundException("Loan not found");
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
        return loanRepository
                .findByStatus(
                        LoanStatus.valueOf(status.toUpperCase()),
                        pageable)
                .map(this::mapToResponse);
    }

    // ── Saga support methods ──────────────────────────────────────

    @Transactional
    public void activateLoan(Long loanId, String transactionRef) {
        Loan loan = getLoan(loanId);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(LocalDate.now());
        loan.setNextEmiDate(LocalDate.now().plusMonths(1));
        loanRepository.save(loan);

        publishLoanDisbursedEvent(loan, transactionRef);
        log.info("Loan {} activated. TransactionRef={}",
                loan.getLoanRef(), transactionRef);
    }

    @Transactional
    public void failLoan(Long loanId, String reason) {
        Loan loan = getLoan(loanId);
        loan.setStatus(LoanStatus.FAILED);
        loan.setRejectionReason("Disbursement failed: " + reason);
        loanRepository.save(loan);

        publishLoanFailedEvent(loan, reason);
        log.warn("Loan {} failed. Reason={}", loan.getLoanRef(),
                reason);
    }

    // ── Outbox event publishers ───────────────────────────────────

    private void publishLoanApprovedEvent(Loan loan) {
        try {
            LoanApprovedEvent event = LoanApprovedEvent.builder()
                    .loanId(loan.getId())
                    .accountId(loan.getAccountId())
                    .customerId(loan.getUserId())
                    .amount(loan.getPrincipalAmount())
                    .loanRef(loan.getLoanRef())
                    .userEmail(loan.getUserEmail())
                    .build();

            OutboxEvent outbox = new OutboxEvent();
            outbox.setTopic(TOPIC_LOAN_EVENTS);
            outbox.setAggregateId(loan.getId().toString());
            outbox.setEventType("LOAN_APPROVED");
            outbox.setPayload(
                    objectMapper.writeValueAsString(event));
            outboxRepository.save(outbox);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize LoanApprovedEvent", e);
        }
    }

    private void publishLoanDisbursedEvent(Loan loan,
                                           String transactionRef) {
        publishGenericLoanEvent(loan, "LOAN_DISBURSED",
                "transactionRef", transactionRef);
    }

    private void publishLoanRejectedEvent(Loan loan) {
        publishGenericLoanEvent(loan, "LOAN_REJECTED",
                "reason", loan.getRejectionReason());
    }

    private void publishLoanFailedEvent(Loan loan, String reason) {
        publishGenericLoanEvent(loan, "LOAN_FAILED",
                "reason", reason);
    }

    private void publishGenericLoanEvent(Loan loan,
                                         String eventType, String extraKey, String extraValue) {
        try {
            java.util.Map<String, Object> payload =
                    new java.util.HashMap<>();
            payload.put("loanId", loan.getId());
            payload.put("loanRef", loan.getLoanRef());
            payload.put("email", loan.getUserEmail());
            payload.put("amount",
                    loan.getPrincipalAmount().toPlainString());
            payload.put("eventType", eventType);
            payload.put(extraKey, extraValue);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setTopic("notification-events");
            outbox.setAggregateId(loan.getId().toString());
            outbox.setEventType(eventType);
            outbox.setPayload(
                    objectMapper.writeValueAsString(payload));
            outboxRepository.save(outbox);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize loan event", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private BigDecimal calculateEmi(BigDecimal principal,
                                    BigDecimal annualRate, int tenureMonths) {

        BigDecimal monthlyRate = annualRate
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        BigDecimal onePlusR =
                BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths,
                new MathContext(20, RoundingMode.HALF_UP));

        BigDecimal numerator = principal
                .multiply(monthlyRate)
                .multiply(onePlusRPowN);

        BigDecimal denominator =
                onePlusRPowN.subtract(BigDecimal.ONE);

        return numerator.divide(denominator, 4,
                RoundingMode.HALF_UP);
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
                    throw new LoanOperationException(
                            "Personal loan: ₹10,000 – ₹5,00,000");
            }
            case HOME_LOAN -> {
                if (amount.compareTo(
                        new BigDecimal("500000")) < 0
                        || amount.compareTo(
                        new BigDecimal("10000000")) > 0)
                    throw new LoanOperationException(
                            "Home loan: ₹5,00,000 – ₹1,00,00,000");
            }
            case CAR_LOAN -> {
                if (amount.compareTo(
                        new BigDecimal("100000")) < 0
                        || amount.compareTo(
                        new BigDecimal("2000000")) > 0)
                    throw new LoanOperationException(
                            "Car loan: ₹1,00,000 – ₹20,00,000");
            }
        }
    }

    private void validateTenureForType(
            LoanType type, int tenureMonths) {
        switch (type) {
            case PERSONAL_LOAN -> {
                if (tenureMonths < 6 || tenureMonths > 60)
                    throw new LoanOperationException(
                            "Personal loan tenure: 6–60 months");
            }
            case HOME_LOAN -> {
                if (tenureMonths < 12 || tenureMonths > 240)
                    throw new LoanOperationException(
                            "Home loan tenure: 12–240 months");
            }
            case CAR_LOAN -> {
                if (tenureMonths < 12 || tenureMonths > 84)
                    throw new LoanOperationException(
                            "Car loan tenure: 12–84 months");
            }
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
                        new LoanNotFoundException(
                                "Loan not found: " + loanId));
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
                .accountNumber(loan.getAccountNumber())
                .createdAt(loan.getCreatedAt())
                .build();
    }
}