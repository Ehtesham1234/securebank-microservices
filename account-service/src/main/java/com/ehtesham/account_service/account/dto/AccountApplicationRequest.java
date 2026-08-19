package com.ehtesham.account_service.account.dto;

import com.ehtesham.account_service.account.enums.AccountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class AccountApplicationRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Initial deposit amount is required")
    @DecimalMin(value = "1000.00",
            message = "Minimum fixed deposit amount is 1000")
    private BigDecimal initialDeposit;
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Minimum duration is 1 month")
    @Max(value = 120, message = "Maximum duration is 120 months")
    private Integer durationMonths;
}