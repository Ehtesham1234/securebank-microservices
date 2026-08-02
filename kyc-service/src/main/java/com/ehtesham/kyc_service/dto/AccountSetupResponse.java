package com.ehtesham.kyc_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * account-service's actual AccountResponse has more fields than this
 * needs (userId, fixedDepositDetails) — ignoreUnknown so deserializing
 * the real response doesn't fail on them. Without this, the kycSetup
 * call would succeed server-side (account + card genuinely created and
 * committed in account-service) but throw here while parsing the
 * response, which surfaces as "Account setup failed" even though it
 * didn't — and leaves the KYC document stuck at PENDING since the
 * exception fires before it's marked VERIFIED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountSetupResponse {
    private Long id;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}