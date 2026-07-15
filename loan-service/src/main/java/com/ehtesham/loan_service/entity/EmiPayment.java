package com.ehtesham.loan_service.entity;


import com.ehtesham.loan_service.enums.EmiStatus;
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
@Table(name = "emi_payments")
public class EmiPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "emi_number", nullable = false)
    private Integer emiNumber;

    @Column(name = "emi_amount",
            nullable = false, precision = 19, scale = 4)
    private BigDecimal emiAmount;

    @Column(name = "interest_component",
            nullable = false, precision = 19, scale = 4)
    private BigDecimal interestComponent;

    @Column(name = "principal_component",
            nullable = false, precision = 19, scale = 4)
    private BigDecimal principalComponent;

    @Column(name = "outstanding_after",
            nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAfter;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmiStatus status;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}