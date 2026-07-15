package com.ehtesham.securebank.websocket.dto;

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
public class BalanceUpdateMessage {

    private String accountNumber;
    private BigDecimal newBalance;
    private BigDecimal transactionAmount;
    private TransactionType transactionType;
    private String transactionRef;
    private String description;
    private LocalDateTime timestamp;
}