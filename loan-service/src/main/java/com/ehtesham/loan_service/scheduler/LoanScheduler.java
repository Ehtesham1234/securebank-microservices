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

import java.time.LocalDate;
import java.util.List;

@Component
public class LoanScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(LoanScheduler.class);

    private static final int MAX_OVERDUE_EMIS_BEFORE_DEFAULT = 3;

    private final LoanRepository loanRepository;
    private final EmiPaymentRepository emiPaymentRepository;

    public LoanScheduler(LoanRepository loanRepository, EmiPaymentRepository emiPaymentRepository) {
        this.loanRepository = loanRepository;
        this.emiPaymentRepository = emiPaymentRepository;
    }
    
    // ── Job 1: Mark overdue EMI payments ─────────────────────────
    // Runs at 2:00 AM every day
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void markOverdueEmiPayments() {
        log.info("SCHEDULER: Checking for overdue EMI payments");

        try {
            List<EmiPayment> overduePayments = emiPaymentRepository
                    .findByStatusAndDueDateBefore(
                            EmiStatus.PENDING, LocalDate.now());

            for (EmiPayment payment : overduePayments) {
                payment.setStatus(EmiStatus.OVERDUE);
                emiPaymentRepository.save(payment);

                checkAndMarkLoanDefaulted(payment.getLoan());
            }

            log.info("SCHEDULER: Marked {} EMI payments as overdue",
                    overduePayments.size());
        } catch (Exception e) {
            log.error("SCHEDULER: Overdue EMI check failed: {}",
                    e.getMessage());
        }
    }
    // ── Private helpers ───────────────────────────────────────────

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
