package com.ehtesham.account_service.card.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class StatementResponse {

    private Long id;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private BigDecimal totalSpent;
    private BigDecimal totalPaid;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal minimumDue;
    private LocalDate dueDate;
    private boolean paid;
    private LocalDateTime createdAt;
}