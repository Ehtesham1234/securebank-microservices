package com.ehtesham.securebank.otp.repository;

import com.ehtesham.securebank.otp.entity.OtpVerification;
import com.ehtesham.securebank.otp.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByEmailAndOtpAndPurposeAndUsedFalse(
            String email, String otp, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpVerification o SET o.used = true " +
            "WHERE o.email = :email AND o.purpose = :purpose " +
            "AND o.used = false")
    void invalidateActiveOtps(String email, OtpPurpose purpose);
    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.used = true " +
            "OR o.expiryDate < :now")
    int deleteExpiredAndUsed(@Param("now") Instant now);
}