package com.ehtesham.securebank.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedDepositResponse {

    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private Integer durationMonths;
    private LocalDate maturityDate;
    private BigDecimal maturityAmount;
}