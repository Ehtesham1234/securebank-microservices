package com.ehtesham.securebank.otp.service;

import com.ehtesham.securebank.otp.enums.OtpPurpose;

public interface OtpService {
    String generateAndSaveOtp(String email, OtpPurpose purpose);
    void verifyOtp(String email, String otp, OtpPurpose purpose);
    void invalidateOtps(String email, OtpPurpose purpose);
}