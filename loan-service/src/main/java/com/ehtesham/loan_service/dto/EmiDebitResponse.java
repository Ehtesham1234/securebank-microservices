package com.ehtesham.loan_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * account-service's actual response — ignoreUnknown so this doesn't
 * break if account-service's DTO ever grows a field this doesn't
 * declare (this exact class of bug already broke kycSetup once —
 * see AccountSetupResponse in kyc-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class EmiDebitResponse {
    private boolean success;
    private BigDecimal newBalance;
    private String transactionRef;
}
