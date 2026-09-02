package com.ehtesham.loan_service.service.impl;

import com.ehtesham.loan_service.client.AccountServiceClient;
import com.ehtesham.loan_service.client.UserSearchClient;
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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final com.ehtesham.loan_service.client.UserStatusClient userStatusClient;
    private final ObjectMapper objectMapper;
    private final UserSearchClient userSearchClient;
    public LoanServiceImpl(
            LoanRepository loanRepository,
            EmiPaymentRepository emiPaymentRepository,
            OutboxRepository outboxRepository,
            AccountServiceClient accountServiceClient,
            com.ehtesham.loan_service.client.UserStatusClient userStatusClient,
            ObjectMapper objectMapper, UserSearchClient userSearchClient) {
        this.loanRepository = loanRepository;
        this.emiPaymentRepository = emiPaymentRepository;
        this.outboxRepository = outboxRepository;
        this.accountServiceClient = accountServiceClient;
        this.userStatusClient = userStatusClient;
        this.objectMapper = objectMapper;
        this.userSearchClient = userSearchClient;
    }

    // Bug fix: closes the same "stale JWT status for up to 15 minutes"
    // gap account-service already closes for deposit/withdraw/transfer —
    // this service had no live re-check at all, so a user suspended
    // mid-session could keep applying for loans and paying EMIs for the
    // rest of their token's lifetime. Fails open (proceeds on the
    // gateway-verified status) if securebank-api can't be reached, same
    // trade-off account-service makes.
    private void verifyLiveUserStatus(Long userId) {
        try {
            com.ehtesham.loan_service.client.InternalUserStatusResponse response =
                    userStatusClient.getUser(userId);

            if (!"ACTIVE".equals(response.getUserStatus())) {
                throw new LoanOperationException(
                        "Your account status no longer permits this " +
                                "operation. Please contact support.");
            }
        } catch (com.ehtesham.loan_service.client.UserStatusCheckUnavailableException e) {
            log.warn("Live user-status check unavailable for userId={}; " +
                    "proceeding on the gateway-verified status from the " +
                    "request token instead.", userId);
        }
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "account-service")
    public LoanResponse applyForLoan(
            LoanApplicationRequest request,
            Long userId, String userEmail) {

        verifyLiveUserStatus(userId);

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

        // C2 defense-in-depth: @PreAuthorize already restricts this
        // endpoint to TELLER/ADMIN, but a teller (or a misconfigured
        // future role) still shouldn't be able to approve their OWN
        // loan application. Checked independently here so it holds even
        // if the annotation is ever loosened or bypassed some other way.
        if (loan.getUserId().equals(reviewerUserId)) {
            throw new LoanOperationException(
                    "You cannot approve your own loan application.");
        }

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

        if (loan.getUserId().equals(reviewerUserId)) {
            throw new LoanOperationException(
                    "You cannot reject your own loan application.");
        }

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

        verifyLiveUserStatus(userId);

        Loan loan = getLoan(loanId);

        if (!loan.getUserId().equals(userId)) {
            throw new LoanNotFoundException("Loan not found");
        }

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new LoanOperationException(
                    "EMI payments only accepted for ACTIVE loans");
        }

        // L3 fix: accountId came from a client-supplied header
        // (X-Account-Id), never verified against anything — the gateway
        // only stamps X-User-Id/Role/Email/Status from the JWT, not this
        // one. Now that this actually triggers a real debit (below),
        // trusting it as-is would let a caller redirect the debit to ANY
        // account by just sending a different X-Account-Id. It must
        // match the account this loan was actually disbursed to.
        if (accountId == null || !accountId.equals(loan.getAccountId())) {
            throw new LoanOperationException(
                    "The provided account does not match this loan's " +
                            "linked account.");
        }

        int newEmiNumber = loan.getEmisPaid() + 1;

        // C4 fix: this used to update loan-service's own records (EMI
        // marked PAID, outstandingAmount reduced, loan possibly CLOSED)
        // and stop there — no money ever actually left the account. This
        // call happens FIRST, before any of loan-service's own state
        // changes below, so a failed debit (insufficient funds, frozen
        // account, service unavailable) means nothing here gets modified
        // either — there's no window where the loan looks paid down but
        // the debit never happened.
        //
        // Bug fix: account-service correctly rejects a genuine
        // double-submit (two near-simultaneous payEmi calls) with a 409
        // via its deterministic "EMI-<loanId>-<emiNumber>" transactionRef
        // unique constraint. But feign.circuitbreaker is enabled
        // bank-wide, and Spring Cloud's Feign+CircuitBreaker wrapping
        // routes ANY exception from the call — including that legitimate
        // 409 — straight to AccountServiceClientFallback, which used to
        // report a generic "service temporarily unavailable" for what is
        // actually "this installment was already paid". Excluding the 4xx
        // FeignException family from the circuit breaker (see
        // application.properties' ignore-exceptions) lets it propagate
        // here instead of being swallowed by the fallback, so it can be
        // given its own clear message.
        EmiDebitResponse debitResult;
        try {
            debitResult = accountServiceClient.debitForEmi(
                    accountId,
                    userId,
                    loanId,
                    newEmiNumber,
                    loan.getEmiAmount(),
                    "EMI #" + newEmiNumber + " for loan " + loan.getLoanRef());
        } catch (feign.FeignException.Conflict e) {
            throw new LoanOperationException(
                    "This EMI installment may already have been paid " +
                            "(a duplicate request was detected). Please " +
                            "refresh your loan status before retrying.");
        }

        if (debitResult == null || !debitResult.isSuccess()) {
            throw new LoanOperationException(
                    "EMI payment could not be processed. Please try again.");
        }

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

        // Bug fix (loan-delinquency detection was dead code): reuse the
        // PENDING placeholder row for this installment if activateLoan()
        // or the previous payEmi() call already materialized one (see
        // createNextPendingInstallment below) — this is what lets
        // LoanScheduler find a real row to flag OVERDUE. Falls back to
        // creating the row fresh for loans that predate this fix (never
        // had a PENDING row seeded for their current installment), so
        // there's no behavior change/migration required for existing
        // active loans.
        EmiPayment payment = emiPaymentRepository
                .findByLoanAndEmiNumber(loan, newEmiNumber)
                .orElseGet(EmiPayment::new);
        payment.setLoan(loan);
        payment.setEmiNumber(newEmiNumber);
        payment.setEmiAmount(loan.getEmiAmount());
        payment.setInterestComponent(interestComponent);
        payment.setPrincipalComponent(principalComponent);
        payment.setOutstandingAfter(newOutstanding);
        if (payment.getDueDate() == null) {
            payment.setDueDate(loan.getNextEmiDate());
        }
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
            // Materialize the PENDING row for the NEXT installment now,
            // while outstanding/nextEmiDate are known — this is what
            // LoanScheduler.markOverdueEmiPayments() finds and flips to
            // OVERDUE if it isn't paid by its due date.
            createNextPendingInstallment(loan, newEmiNumber + 1);
        }

        return mapToResponse(loanRepository.save(loan));
    }
    @Override
    @Transactional(readOnly = true)
    public Page<LoanResponse> getAllLoans(
            Long userId, Long loanId, String loanRef, String search, Pageable pageable) {
        if (loanId != null) {
            return loanRepository.findById(loanId)
                    .map(l -> (Page<LoanResponse>) new PageImpl<>(List.of(mapToResponse(l)), pageable, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
        }
        if (userId != null) {
            return loanRepository.findByUserId(userId, pageable).map(this::mapToResponse);
        }
        if (loanRef != null && !loanRef.isBlank()) {
            return loanRepository.findByLoanRefContainingIgnoreCase(loanRef.trim(), pageable).map(this::mapToResponse);
        }
        if (search != null && !search.isBlank()) {
            List<Long> userIds = userSearchClient.searchUserIds(search.trim());
            if (userIds.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            return loanRepository.findByUserIdIn(userIds, pageable).map(this::mapToResponse);
        }
        return loanRepository.findAll(pageable).map(this::mapToResponse);
    }

    // Bug fix (loan-delinquency detection was dead code): pre-materializes
    // the PENDING EmiPayment row for the given installment, using the
    // same interest/principal split payEmi() would compute at payment
    // time. Safe to precompute here because nothing else mutates
    // loan.outstandingAmount between now and that installment's payment —
    // EMIs are paid strictly in order, one at a time.
    // Bug fix (regression from the v13 delinquency-detection fix): this
    // used to always `new EmiPayment()` with no existence check. It's
    // called from two places that can each legitimately run twice for
    // the same installment — LoanSagaConsumer's @KafkaListener (standard
    // at-least-once delivery: a redelivered "loan activated" message
    // re-runs activateLoan()) and payEmi()'s own retry path (a client
    // retry after a timeout hits debitForEmi's cached-success fast path,
    // but the rest of payEmi() still re-executes). Without this
    // existence check, either path inserts a SECOND row for the same
    // (loan, emiNumber) — and since findByLoanAndEmiNumber() returns
    // Optional (expects at most one match), two rows throws
    // IncorrectResultSizeDataAccessException everywhere that's called,
    // including LoanScheduler's daily job. See also the unique
    // constraint added as a backstop (V3__add_emi_payment_unique_constraint.sql).
    private void createNextPendingInstallment(
            Loan loan, int emiNumber) {

        if (emiPaymentRepository
                .findByLoanAndEmiNumber(loan, emiNumber)
                .isPresent()) {
            // Already materialized by an earlier attempt — nothing to do.
            return;
        }

        BigDecimal monthlyRate = loan.getInterestRate()
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        BigDecimal interestComponent = loan.getOutstandingAmount()
                .multiply(monthlyRate)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal principalComponent = loan.getEmiAmount()
                .subtract(interestComponent)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal projectedOutstandingAfter = loan.getOutstandingAmount()
                .subtract(principalComponent)
                .setScale(4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);

        EmiPayment payment = new EmiPayment();
        payment.setLoan(loan);
        payment.setEmiNumber(emiNumber);
        payment.setEmiAmount(loan.getEmiAmount());
        payment.setInterestComponent(interestComponent);
        payment.setPrincipalComponent(principalComponent);
        payment.setOutstandingAfter(projectedOutstandingAfter);
        payment.setDueDate(loan.getNextEmiDate());
        payment.setStatus(EmiStatus.PENDING);
        emiPaymentRepository.save(payment);
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

        // Bug fix: this had no idempotency guard at all — the caller
        // (LoanSagaConsumer) is a @KafkaListener, standard at-least-once
        // delivery. A redelivered message re-running this would reset
        // disbursementDate to "now" and nextEmiDate to now+1month even
        // for a loan that's already been active for months (corrupting
        // both), on top of the duplicate-PENDING-row problem that's now
        // fixed separately in createNextPendingInstallment(). If it's
        // already past PENDING_DISBURSEMENT, this message has already
        // been processed — log and return rather than re-running the
        // activation.
        if (loan.getStatus() != LoanStatus.APPROVED) {
            log.info("Ignoring redelivered/duplicate activation for " +
                    "loan {} — already in status {}",
                    loan.getLoanRef(), loan.getStatus());
            return;
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(LocalDate.now());
        loan.setNextEmiDate(LocalDate.now().plusMonths(1));
        loanRepository.save(loan);

        // Bug fix (loan-delinquency detection was dead code): seed the
        // PENDING row for installment #1 so there's something for
        // LoanScheduler.markOverdueEmiPayments() to find and flag OVERDUE
        // if the customer never pays it — see createNextPendingInstallment.
        createNextPendingInstallment(loan, 1);

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
                .userId(loan.getUserId())
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