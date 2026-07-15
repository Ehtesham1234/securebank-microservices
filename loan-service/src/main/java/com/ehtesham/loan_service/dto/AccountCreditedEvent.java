package com.ehtesham.loan_service.dto;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountCreditedEvent {
    private Long loanId;
    private Long accountId;
    private BigDecimal newBalance;
    private String transactionRef;
    private boolean success;
    private String failureReason;
}