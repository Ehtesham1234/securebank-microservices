package com.ehtesham.loan_service.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccountValidationResponse {
    private Long accountId;
    private boolean valid;
    private boolean unavailable;
    private String accountNumber;
    private BigDecimal currentBalance;
    private String reason;
}