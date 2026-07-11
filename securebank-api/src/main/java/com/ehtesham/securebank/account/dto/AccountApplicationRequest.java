package com.ehtesham.securebank.account.dto;

import com.ehtesham.securebank.common.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class AccountApplicationRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    // only required for FIXED_DEPOSIT
    @DecimalMin(
            value = "1000.00",
            message = "Minimum fixed deposit amount is 1000")
    private BigDecimal initialDeposit;

    // only required for FIXED_DEPOSIT — duration in months
    @Min(value = 1, message = "Minimum duration is 1 month")
    @Max(value = 120, message = "Maximum duration is 120 months")
    private Integer durationMonths;
}