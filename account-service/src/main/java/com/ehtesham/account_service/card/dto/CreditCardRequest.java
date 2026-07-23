package com.ehtesham.account_service.card.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class CreditCardRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Card holder name is required")
    private String cardHolderName;

    @NotNull(message = "Credit limit is required")
    @DecimalMin(value = "10000.00",
            message = "Minimum credit limit is ₹10,000")
    @DecimalMax(value = "1000000.00",
            message = "Maximum credit limit is ₹10,00,000")
    private BigDecimal creditLimit;

    @NotNull(message = "Billing cycle day is required")
    @Min(value = 1) @Max(value = 28)
    private Integer billingCycleDay;
}