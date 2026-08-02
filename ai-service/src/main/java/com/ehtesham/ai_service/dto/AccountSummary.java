package com.ehtesham.ai_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// H3 fix: real response DTOs have more fields than this summary
// needs — don't let deserialization break when they do.
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountSummary {
    private Long id;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
}
