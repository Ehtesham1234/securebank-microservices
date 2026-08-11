package com.ehtesham.loan_service.repository;

import com.ehtesham.loan_service.entity.EmiPayment;
import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.EmiStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmiPaymentRepository
        extends JpaRepository<EmiPayment, Long> {

    List<EmiPayment> findByLoanOrderByEmiNumberAsc(Loan loan);

    List<EmiPayment> findByStatusAndDueDateBefore(
            EmiStatus status, LocalDate date);

    long countByLoanAndStatus(Loan loan, EmiStatus status);

    // Bug fix: lets payEmi() find the PENDING placeholder row for the
    // installment it's paying (if one was pre-materialized by
    // activateLoan()/a previous payEmi() call) instead of always blindly
    // inserting a new row — and lets the scheduler look up "the row for
    // the currently-due installment" directly.
    Optional<EmiPayment> findByLoanAndEmiNumber(
            Loan loan, Integer emiNumber);
}