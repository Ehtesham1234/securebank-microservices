package com.ehtesham.loan_service.repository;

import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepository
        extends JpaRepository<Loan, Long> {

    Page<Loan> findByUserId(Long userId, Pageable pageable);

    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    Optional<Loan> findByLoanRef(String loanRef);

    boolean existsByUserIdAndStatusIn(
            Long userId, List<LoanStatus> statuses);

    // Bug fix (loan-delinquency detection was dead code): drives
    // LoanScheduler.markOverdueEmiPayments() directly off the Loan's own
    // nextEmiDate rather than an EmiPayment "PENDING" row that nothing in
    // the codebase ever actually created — see LoanScheduler for the full
    // explanation.
    List<Loan> findByStatusAndNextEmiDateBefore(
            LoanStatus status, LocalDate date);

    Page<Loan> findByLoanRefContainingIgnoreCase(String loanRef, Pageable pageable);

    Page<Loan> findByUserIdIn(List<Long> userIds, Pageable pageable);
}