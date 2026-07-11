package com.ehtesham.securebank.account.entity;

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
@Table(name = "fixed_deposit_details")
public class FixedDepositDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "principal_amount",
            nullable = false,
            precision = 19,
            scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate",
            nullable = false,
            precision = 5,
            scale = 2)
    private BigDecimal interestRate;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "maturity_amount",
            nullable = false,
            precision = 19,
            scale = 4)
    private BigDecimal maturityAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}