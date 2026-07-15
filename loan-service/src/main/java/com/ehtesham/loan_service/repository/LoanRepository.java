package com.ehtesham.loan_service.repository;

import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanRepository
        extends JpaRepository<Loan, Long> {

    Page<Loan> findByUserId(Long userId, Pageable pageable);

    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    Optional<Loan> findByLoanRef(String loanRef);

    boolean existsByUserIdAndStatusIn(
            Long userId, List<LoanStatus> statuses);
}