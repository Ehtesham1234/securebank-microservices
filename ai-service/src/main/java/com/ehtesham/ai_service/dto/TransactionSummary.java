package com.ehtesham.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class TransactionSummary {
    private String transactionRef;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String status;
    private String description;
    private LocalDateTime createdAt;
}
