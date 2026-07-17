package com.ehtesham.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountSummary {
    private Long id;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
}
