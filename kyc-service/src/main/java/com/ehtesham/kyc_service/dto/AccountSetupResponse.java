package com.ehtesham.kyc_service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountSetupResponse {
    private Long id;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}