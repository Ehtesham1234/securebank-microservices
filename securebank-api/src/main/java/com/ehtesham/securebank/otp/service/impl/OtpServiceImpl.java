package com.ehtesham.securebank.otp.service.impl;

import com.ehtesham.securebank.common.exception.InvalidOtpException;
import com.ehtesham.securebank.otp.entity.OtpVerification;
import com.ehtesham.securebank.otp.enums.OtpPurpose;
import com.ehtesham.securebank.otp.repository.OtpVerificationRepository;
import com.ehtesham.securebank.otp.service.OtpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpServiceImpl implements OtpService {

    private static final long OTP_EXPIRY_MINUTES = 10;

    // C3 fix: independent of the 10-minute time-based expiry, an OTP is
    // locked out after this many wrong guesses so the full 6-digit space
    // (1,000,000 combinations) can't be brute-forced within its lifetime.
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // C3 hardening: java.util.Random is a predictable PRNG (its internal
    // state can be recovered from a handful of outputs) — not appropriate
    // for a value that gates password resets and account takeover.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpRepository;

    public OtpServiceImpl(OtpVerificationRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    @Override
    @Transactional
    public String generateAndSaveOtp(String email, OtpPurpose purpose) {

        otpRepository.invalidateActiveOtps(email, purpose);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        OtpVerification entity = new OtpVerification();
        entity.setEmail(email);
        entity.setOtp(otp);
        entity.setPurpose(purpose);
        entity.setExpiryDate(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60));
        entity.setUsed(false);
        entity.setFailedAttempts(0);

        otpRepository.save(entity);

        return otp;
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp, OtpPurpose purpose) {

        OtpVerification entity = otpRepository
                .findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() ->
                        new InvalidOtpException("Invalid or expired OTP"));

        if (entity.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP has expired");
        }

        // C3 fix: lock out this OTP once too many wrong guesses have been
        // made against it, rather than leaving it guessable for the rest
        // of its 10-minute window.
        if (entity.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            entity.setUsed(true);
            otpRepository.save(entity);
            throw new InvalidOtpException(
                    "Too many incorrect attempts. Please request a new code.");
        }

        // Bug fix: .equals() short-circuits on the first mismatched
        // character, which leaks (via response timing) how many leading
        // digits of a guess were correct — a real, if minor, side
        // channel for a 6-digit code. CvvService already uses
        // MessageDigest.isEqual for exactly this reason; do the same
        // here for consistency. (Low practical risk given the lockout
        // above, but no reason to be inconsistent.)
        if (!java.security.MessageDigest.isEqual(
                entity.getOtp().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                otp.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            entity.setFailedAttempts(entity.getFailedAttempts() + 1);
            otpRepository.save(entity);
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        entity.setUsed(true);
        otpRepository.save(entity);
    }

    @Override
    @Transactional
    public void invalidateOtps(String email, OtpPurpose purpose) {
        otpRepository.invalidateActiveOtps(email, purpose);
    }
}