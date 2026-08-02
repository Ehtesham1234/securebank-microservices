package com.ehtesham.ai_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// H3 fix: real response DTOs have more fields than this summary
// needs — don't let deserialization break when they do.
@JsonIgnoreProperties(ignoreUnknown = true)
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
