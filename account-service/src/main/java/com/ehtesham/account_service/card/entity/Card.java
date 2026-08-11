package com.ehtesham.account_service.card.entity;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import com.ehtesham.account_service.card.security.PanEncryptionConverter;
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

    // C5 fix: the PAN is encrypted at rest (AES-256-GCM) — never stored or
    // logged in plaintext. maskedNumber below is what's safe to display.
    @Convert(converter = PanEncryptionConverter.class)
    @Column(name = "card_number", nullable = false, length = 255)
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

    // L1 fix: independent of the CVV itself never being stored (C5) —
    // this tracks wrong VERIFICATION guesses so the endpoint can't be
    // brute-forced (3 digits = 1000 combinations) if a session is ever
    // compromised without the physical card.
    @Column(name = "cvv_failed_attempts", nullable = false)
    private int cvvFailedAttempts = 0;

    @Column(name = "cvv_locked_until")
    private java.time.LocalDateTime cvvLockedUntil;

    // C5 fix: cvv_hash removed entirely. A CVV must never be retained
    // after issuance/authorization in ANY form — hashed included — per
    // PCI-DSS 3.2. It's generated, shown to the user once at issuance, and
    // discarded; nothing here re-validates it later, so there was nothing
    // depending on persisting it.

    // DEBIT_CARD — daily spending limit
    // NOTE: currently informational only, not enforced anywhere. There
    // is no endpoint that spends against a debit card specifically
    // (account withdraw/transfer aren't wired to a Card at all), so
    // there's nowhere in the current architecture to check this against.
    // Left as-is rather than silently enforced, so as not to introduce
    // new spend-blocking behavior as a side effect of a bug-fix pass —
    // enforcing it properly needs a real "debit card transaction"
    // endpoint first.
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    // CREDIT_CARD specific fields
    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "available_credit", precision = 19, scale = 4)
    private BigDecimal availableCredit;

    @Column(name = "outstanding_bill", precision = 19, scale = 4)
    private BigDecimal outstandingBill = BigDecimal.ZERO;

    // C6 fix: spend() increments this by each purchase amount.
    // generateMonthlyStatements() previously derived "this cycle's spend"
    // as (creditLimit - availableCredit) instead of reading it directly —
    // but that expression is always just the CURRENT outstandingBill
    // (availableCredit is kept as creditLimit - outstandingBill at all
    // times by spend()/payCreditCardBill()), so it silently doubled the
    // closing balance every single statement. This field is the actual
    // "spend since last statement" counter; it's reset to zero once a
    // statement is generated, independent of outstandingBill/availableCredit.
    @Column(name = "cycle_spend", precision = 19, scale = 4)
    private BigDecimal cycleSpend = BigDecimal.ZERO;

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