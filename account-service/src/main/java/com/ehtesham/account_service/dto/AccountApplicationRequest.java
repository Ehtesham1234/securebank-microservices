package com.ehtesham.account_service.dto;

import com.ehtesham.account_service.enums.AccountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class AccountApplicationRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @DecimalMin(value = "1000.00",
            message = "Minimum fixed deposit amount is 1000")
    private BigDecimal initialDeposit;

    @Min(value = 1, message = "Minimum duration is 1 month")
    @Max(value = 120, message = "Maximum duration is 120 months")
    private Integer durationMonths;
}