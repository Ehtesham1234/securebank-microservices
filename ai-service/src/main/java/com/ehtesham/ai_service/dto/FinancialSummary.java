package com.ehtesham.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured output — the AI returns this typed object for financial
 * summaries instead of raw text. Spring AI's structured output support
 * deserializes the model's JSON response directly into this POJO.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class FinancialSummary {
    private BigDecimal totalBalance;
    private BigDecimal totalOutstandingLoans;
    private BigDecimal nextEmiAmount;
    private BigDecimal totalSpentThisMonth;
    private BigDecimal totalReceivedThisMonth;
    private String financialHealthStatus;  // "HEALTHY", "CAUTION", "AT_RISK"
    private List<String> recommendations;
}
