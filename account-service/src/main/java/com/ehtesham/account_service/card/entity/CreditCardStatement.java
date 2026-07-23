package com.ehtesham.account_service.card.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "credit_card_statements")
public class CreditCardStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "total_spent", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal totalSpent;

    @Column(name = "total_paid", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "opening_balance", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "minimum_due", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal minimumDue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid", nullable = false)
    private boolean paid = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}