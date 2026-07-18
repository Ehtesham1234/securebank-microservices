package com.ehtesham.securebank.scheduler.service;

import com.ehtesham.securebank.auth.repository.RefreshTokenRepository;
import com.ehtesham.securebank.otp.repository.OtpVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class BankingScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(BankingScheduler.class);

    private static final int MAX_OVERDUE_EMIS_BEFORE_DEFAULT = 3;

//    private final CardService cardService;
//    private final CardRepository cardRepository;
//    private final LoanRepository loanRepository;
//    private final EmiPaymentRepository emiPaymentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    public BankingScheduler(
//            CardService cardService,
//            CardRepository cardRepository,
//            LoanRepository loanRepository,
//            EmiPaymentRepository emiPaymentRepository,
            RefreshTokenRepository refreshTokenRepository, OtpVerificationRepository otpVerificationRepository) {
//        this.cardService = cardService;
//        this.cardRepository = cardRepository;
//        this.loanRepository = loanRepository;
//        this.emiPaymentRepository = emiPaymentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpVerificationRepository = otpVerificationRepository;
    }

    // ── Job 1: Generate monthly credit card statements ────────────
    // Runs at 1:00 AM every day
    // Each card's billing cycle day is checked inside the method
//    @Scheduled(cron = "0 0 1 * * *")
//    public void generateCreditCardStatements() {
//        log.info("SCHEDULER: Starting monthly credit card " +
//                "statement generation");
//        try {
//            cardService.generateMonthlyStatements();
//            log.info("SCHEDULER: Monthly statements generated");
//        } catch (Exception e) {
//            log.error("SCHEDULER: Statement generation failed: {}",
//                    e.getMessage());
//        }
//    }

    // ── Job 2: Mark overdue EMI payments ─────────────────────────
    // Runs at 2:00 AM every day
//    @Scheduled(cron = "0 0 2 * * *")
//    @Transactional
//    public void markOverdueEmiPayments() {
//        log.info("SCHEDULER: Checking for overdue EMI payments");
//
//        try {
//            List<EmiPayment> overduePayments = emiPaymentRepository
//                    .findByStatusAndDueDateBefore(
//                            EmiStatus.PENDING, LocalDate.now());
//
//            for (EmiPayment payment : overduePayments) {
//                payment.setStatus(EmiStatus.OVERDUE);
//                emiPaymentRepository.save(payment);
//
//                checkAndMarkLoanDefaulted(payment.getLoan());
//            }
//
//            log.info("SCHEDULER: Marked {} EMI payments as overdue",
//                    overduePayments.size());
//        } catch (Exception e) {
//            log.error("SCHEDULER: Overdue EMI check failed: {}",
//                    e.getMessage());
//        }
//    }

//    // ── Job 3: Mark expired cards ─────────────────────────────────
//    // Runs at 3:00 AM every day
//    @Scheduled(cron = "0 0 3 * * *")
//    @Transactional
//    public void markExpiredCards() {
//        log.info("SCHEDULER: Checking for expired cards");
//
//        try {
//            List<com.ehtesham.securebank.card.entity.Card> expiredCards =
//                    cardRepository.findExpiredActiveCards(LocalDate.now());
//
//            for (var card : expiredCards) {
//                card.setStatus(CardStatus.EXPIRED);
//                cardRepository.save(card);
//            }
//
//            log.info("SCHEDULER: Marked {} cards as expired",
//                    expiredCards.size());
//        } catch (Exception e) {
//            log.error("SCHEDULER: Card expiry check failed: {}",
//                    e.getMessage());
//        }
//    }

    // ── Job 4: Clean up expired refresh tokens ───────────────────
    // Runs at 4:00 AM every day
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("SCHEDULER: Cleaning up expired refresh tokens");

        try {
            int deleted = refreshTokenRepository
                    .deleteExpiredAndRevoked(Instant.now());

            log.info("SCHEDULER: Deleted {} expired refresh tokens",
                    deleted);
        } catch (Exception e) {
            log.error("SCHEDULER: Token cleanup failed: {}",
                    e.getMessage());
        }
    }

    // ── Job 5:Clean up expired/used OTP records ───────────────────
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("SCHEDULER: Starting OTP cleanup");
        try {
            int deleted = otpVerificationRepository
                    .deleteExpiredAndUsed(Instant.now());
            log.info("SCHEDULER: Deleted {} expired/used OTP records",
                    deleted);
        } catch (Exception e) {
            log.error("SCHEDULER: OTP cleanup failed: {}",
                    e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────

//    private void checkAndMarkLoanDefaulted(Loan loan) {
//        long overdueCount = emiPaymentRepository
//                .countByLoanAndStatus(loan, EmiStatus.OVERDUE);
//
//        if (overdueCount >= MAX_OVERDUE_EMIS_BEFORE_DEFAULT
//                && loan.getStatus() == LoanStatus.ACTIVE) {
//            loan.setStatus(LoanStatus.DEFAULTED);
//            loanRepository.save(loan);
//            log.warn("SCHEDULER: Loan {} marked as DEFAULTED " +
//                            "after {} overdue payments",
//                    loan.getLoanRef(), overdueCount);
//        }
//    }
}
