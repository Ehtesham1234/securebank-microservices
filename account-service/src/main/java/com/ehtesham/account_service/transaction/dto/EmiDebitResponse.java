package com.ehtesham.account_service.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class EmiDebitResponse {
    private boolean success;
    private BigDecimal newBalance;
    private String transactionRef;
}
