package com.ehtesham.securebank.loan.dto;

import com.ehtesham.securebank.common.enums.LoanType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LoanApplicationRequest {

    @NotNull(message = "Loan type is required")
    private LoanType loanType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000.00",
            message = "Minimum loan amount is ₹10,000")
    private BigDecimal amount;

    @NotNull(message = "Tenure is required")
    @Min(value = 6, message = "Minimum tenure is 6 months")
    @Max(value = 240, message = "Maximum tenure is 240 months")
    private Integer tenureMonths;

    @NotBlank(message = "Purpose is required")
    @Size(max = 500, message = "Purpose cannot exceed 500 characters")
    private String purpose;

    @NotNull(message = "Account ID is required")
    private Long accountId;
}