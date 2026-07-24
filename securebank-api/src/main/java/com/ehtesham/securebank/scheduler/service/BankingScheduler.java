package com.ehtesham.securebank.scheduler.service;

import com.ehtesham.securebank.auth.repository.RefreshTokenRepository;
import com.ehtesham.securebank.otp.repository.OtpVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class BankingScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(BankingScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    public BankingScheduler(
            RefreshTokenRepository refreshTokenRepository, OtpVerificationRepository otpVerificationRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpVerificationRepository = otpVerificationRepository;
    }


    // ── Job 1: Clean up expired refresh tokens ───────────────────
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

    // ── Job 2:Clean up expired/used OTP records ───────────────────
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
}
