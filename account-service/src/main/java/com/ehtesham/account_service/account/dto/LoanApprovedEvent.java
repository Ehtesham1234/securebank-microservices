package com.ehtesham.account_service.account.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class LoanApprovedEvent {
    private Long loanId;
    private Long accountId;
    private Long customerId;
    private BigDecimal amount;
    private String loanRef;
}