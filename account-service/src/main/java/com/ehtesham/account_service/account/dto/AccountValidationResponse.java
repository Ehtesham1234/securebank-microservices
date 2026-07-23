package com.ehtesham.account_service.account.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountValidationResponse {
    private Long accountId;
    private boolean valid;
    private boolean unavailable;   // true when service is down
    private String accountNumber;
    private BigDecimal currentBalance;
    private String reason;         // why invalid, if valid=false
}