package com.ehtesham.loan_service.entity;


import com.ehtesham.loan_service.enums.LoanStatus;
import com.ehtesham.loan_service.enums.LoanType;
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
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_ref", nullable = false, unique = true)
    private String loanRef;

    // Plain IDs instead of @ManyToOne entity references
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @Column(name = "principal_amount",
            nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate",
            nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "emi_amount", precision = 19, scale = 4)
    private BigDecimal emiAmount;

    @Column(name = "total_payable_amount", precision = 19, scale = 4)
    private BigDecimal totalPayableAmount;

    @Column(name = "outstanding_amount", precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(name = "emis_paid", nullable = false)
    private Integer emisPaid = 0;

    @Column(name = "next_emi_date")
    private LocalDate nextEmiDate;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "purpose")
    private String purpose;

    // User email stored for notifications (denormalized)
    @Column(name = "user_email")
    private String userEmail;

    // Account number stored for display (denormalized)
    @Column(name = "account_number")
    private String accountNumber;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}