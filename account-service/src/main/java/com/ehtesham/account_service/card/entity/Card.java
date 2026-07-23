package com.ehtesham.account_service.card.entity;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @Column(name = "masked_number", nullable = false)
    private String maskedNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "cvv_hash", nullable = false)
    private String cvvHash;

    // DEBIT_CARD — daily spending limit
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    // CREDIT_CARD specific fields
    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "available_credit", precision = 19, scale = 4)
    private BigDecimal availableCredit;

    @Column(name = "outstanding_bill", precision = 19, scale = 4)
    private BigDecimal outstandingBill = BigDecimal.ZERO;

    @Column(name = "billing_cycle_day")
    private Integer billingCycleDay;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}