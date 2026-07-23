package com.ehtesham.account_service.card.dto;


import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CardResponse {

    private Long id;
    private String maskedNumber;       // never expose full number
    private CardType cardType;
    private CardStatus status;
    private LocalDate expiryDate;
    private String cardHolderName;
    private BigDecimal dailyLimit;     // debit only
    private BigDecimal creditLimit;    // credit only
    private BigDecimal availableCredit; // credit only
    private BigDecimal outstandingBill; // credit only
    private LocalDate dueDate;          // credit only
    private String accountNumber;
    private LocalDateTime createdAt;
}