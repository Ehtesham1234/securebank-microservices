package com.ehtesham.securebank.transaction.dto;

import com.ehtesham.securebank.common.enums.TransactionStatus;
import com.ehtesham.securebank.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private Long id;
    private String transactionRef;
    private String accountNumber;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private TransactionStatus status;
    private String description;
    private String relatedAccountNumber;   // nullable
    private LocalDateTime createdAt;
}