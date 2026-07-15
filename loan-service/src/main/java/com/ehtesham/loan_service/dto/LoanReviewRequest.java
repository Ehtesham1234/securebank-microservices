package com.ehtesham.loan_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class LoanReviewRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}