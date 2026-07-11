package com.ehtesham.securebank.otp.service.impl;

import com.ehtesham.securebank.common.exception.InvalidOtpException;
import com.ehtesham.securebank.otp.entity.OtpVerification;
import com.ehtesham.securebank.otp.enums.OtpPurpose;
import com.ehtesham.securebank.otp.repository.OtpVerificationRepository;
import com.ehtesham.securebank.otp.service.OtpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;

@Service
public class OtpServiceImpl implements OtpService {

    private static final long OTP_EXPIRY_MINUTES = 10;
    private final OtpVerificationRepository otpRepository;

    public OtpServiceImpl(OtpVerificationRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    @Override
    @Transactional
    public String generateAndSaveOtp(String email, OtpPurpose purpose) {

        otpRepository.invalidateActiveOtps(email, purpose);

        String otp = String.format("%06d", new Random().nextInt(999999));

        OtpVerification entity = new OtpVerification();
        entity.setEmail(email);
        entity.setOtp(otp);
        entity.setPurpose(purpose);
        entity.setExpiryDate(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60));
        entity.setUsed(false);

        otpRepository.save(entity);

        return otp;
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp, OtpPurpose purpose) {

        OtpVerification entity = otpRepository
                .findByEmailAndOtpAndPurposeAndUsedFalse(email, otp, purpose)
                .orElseThrow(() ->
                        new InvalidOtpException("Invalid or expired OTP"));

        if (entity.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP has expired");
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