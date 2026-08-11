package com.ehtesham.loan_service.scheduler;

import com.ehtesham.loan_service.entity.EmiPayment;
import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.EmiStatus;
import com.ehtesham.loan_service.enums.LoanStatus;
import com.ehtesham.loan_service.repository.EmiPaymentRepository;
import com.ehtesham.loan_service.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/*
 * Bug fix: this job used to query EmiPayment rows with status=PENDING —
 * but nothing anywhere in loan-service ever created an EmiPayment row
 * with that status. The only place `new EmiPayment()` was constructed
 * was payEmi(), and it was saved as PAID immediately. So this query
 * always returned an empty list: a customer could simply never pay an
 * EMI and nothing would ever flag it — no OVERDUE, no DEFAULTED,
 * loan.nextEmiDate just sat in the past forever.
 *
 * Two changes fix this:
 *   1. LoanServiceImpl now pre-materializes a PENDING EmiPayment row for
 *      the next installment (at activation, and after each successful
 *      payment) — see createNextPendingInstallment() there.
 *   2. This job no longer depends on that row existing. It queries
 *      Loan.nextEmiDate directly (the real source of truth for "is this
 *      loan behind"), and self-heals by creating the PENDING row on the
 *      spot if one wasn't pre-materialized — which also makes it work
 *      correctly for loans that were activated before this fix, with no
 *      backfill/migration needed.
 */
@Component
public class LoanScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(LoanScheduler.class);

    private static final int MAX_OVERDUE_EMIS_BEFORE_DEFAULT = 3;

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LoanRepository loanRepository;
    private final EmiPaymentRepository emiPaymentRepository;

    // Bug fix: self-injection (via a @Lazy proxy) so
    // markOverdueEmiPayments() can call markCurrentInstallmentOverdue()
    // THROUGH Spring's proxy — needed for its
    // @Transactional(REQUIRES_NEW) to actually take effect, the same
    // pattern used for CardServiceImpl.generateStatementForCard() and
    // AccountServiceImpl.payOutMaturedFixedDeposit(). Without this, one
    // loan whose overdue-processing throws (e.g. a bad interest-rate
    // value blowing up the BigDecimal math) would abort the whole day's
    // run for every other loan, since this job previously had no
    // per-loan isolation at all.
    private final LoanScheduler self;

    public LoanScheduler(
            LoanRepository loanRepository,
            EmiPaymentRepository emiPaymentRepository,
            @org.springframework.context.annotation.Lazy LoanScheduler self) {
        this.loanRepository = loanRepository;
        this.emiPaymentRepository = emiPaymentRepository;
        this.self = self;
    }
    
    // ── Job 1: Mark overdue EMI payments ─────────────────────────
    // Runs at 2:00 AM every day
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional(readOnly = true)
    public void markOverdueEmiPayments() {
        log.info("SCHEDULER: Checking for overdue EMI payments");

        List<Long> overdueLoanIds = loanRepository
                .findByStatusAndNextEmiDateBefore(
                        LoanStatus.ACTIVE, LocalDate.now())
                .stream()
                .map(Loan::getId)
                .collect(java.util.stream.Collectors.toList());

        int marked = 0;
        for (Long loanId : overdueLoanIds) {
            try {
                if (self.markCurrentInstallmentOverdue(loanId)) {
                    marked++;
                }
            } catch (Exception e) {
                log.error("SCHEDULER: Failed to process overdue check " +
                        "for loanId={}: {}", loanId, e.getMessage(), e);
            }
        }

        log.info("SCHEDULER: Marked {} EMI payments as overdue " +
                        "({}/{} loans processed successfully)",
                marked, overdueLoanIds.size(), overdueLoanIds.size());
    }
    // ── Private helpers ───────────────────────────────────────────

    @Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean markCurrentInstallmentOverdue(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalStateException(
                        "Loan not found: " + loanId));

        int dueEmiNumber = loan.getEmisPaid() + 1;

        EmiPayment payment = emiPaymentRepository
                .findByLoanAndEmiNumber(loan, dueEmiNumber)
                .orElseGet(() -> materializePendingInstallment(loan, dueEmiNumber));

        // Already PAID (shouldn't happen given the nextEmiDate filter,
        // but the row could be stale if payEmi() ran between the query
        // and here), already OVERDUE, or WAIVED — nothing to do.
        if (payment.getStatus() != EmiStatus.PENDING) {
            return false;
        }

        payment.setStatus(EmiStatus.OVERDUE);
        emiPaymentRepository.save(payment);

        checkAndMarkLoanDefaulted(loan);
        return true;
    }

    // Self-healing fallback for a loan whose currently-due installment
    // never got a PENDING row pre-materialized (e.g. it was activated
    // before this fix shipped) — computes the same interest/principal
    // split LoanServiceImpl uses, from the loan's current
    // outstandingAmount, which hasn't moved since nothing has been paid
    // toward this installment yet.
    private EmiPayment materializePendingInstallment(
            Loan loan, int emiNumber) {

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
        return emiPaymentRepository.save(payment);
    }

    private void checkAndMarkLoanDefaulted(Loan loan) {
        long overdueCount = emiPaymentRepository
                .countByLoanAndStatus(loan, EmiStatus.OVERDUE);

        if (overdueCount >= MAX_OVERDUE_EMIS_BEFORE_DEFAULT
                && loan.getStatus() == LoanStatus.ACTIVE) {
            loan.setStatus(LoanStatus.DEFAULTED);
            loanRepository.save(loan);
            log.warn("SCHEDULER: Loan {} marked as DEFAULTED " +
                            "after {} overdue payments",
                    loan.getLoanRef(), overdueCount);
        }
    }
}
