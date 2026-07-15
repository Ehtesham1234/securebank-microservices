package com.ehtesham.account_service.dto;


import com.ehtesham.account_service.enums.AccountStatus;
import com.ehtesham.account_service.enums.AccountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private Long userId;           // userId instead of ownerEmail
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private FixedDepositResponse fixedDepositDetails;
    private LocalDateTime createdAt;
}