package com.ehtesham.loan_service.dto;

import com.ehtesham.loan_service.enums.LoanStatus;
import com.ehtesham.loan_service.enums.LoanType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class LoanResponse {
    private Long id;
    private String loanRef;
    private LoanType loanType;
    private LoanStatus status;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private BigDecimal totalPayableAmount;
    private BigDecimal outstandingAmount;
    private Integer emisPaid;
    private Integer emisRemaining;
    private LocalDate nextEmiDate;
    private LocalDate disbursementDate;
    private String rejectionReason;
    private String purpose;
    private String accountNumber;
    private LocalDateTime createdAt;
}