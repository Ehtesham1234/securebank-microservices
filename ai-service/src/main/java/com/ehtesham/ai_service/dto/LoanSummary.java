package com.ehtesham.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class LoanSummary {
    private String loanRef;
    private String loanType;
    private String status;
    private BigDecimal principalAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal emiAmount;
    private Integer emisPaid;
    private Integer emisRemaining;
    private LocalDate nextEmiDate;
}
