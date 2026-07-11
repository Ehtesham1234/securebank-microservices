package com.ehtesham.securebank.loan.repository;

import com.ehtesham.securebank.common.enums.LoanStatus;
import com.ehtesham.securebank.loan.entity.Loan;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUser(User user, Pageable pageable);

    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    Optional<Loan> findByLoanRef(String loanRef);

    boolean existsByUserAndStatusIn(
            User user, java.util.List<LoanStatus> statuses);
}