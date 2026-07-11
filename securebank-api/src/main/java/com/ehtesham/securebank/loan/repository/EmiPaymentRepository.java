package com.ehtesham.securebank.loan.repository;

import com.ehtesham.securebank.common.enums.EmiStatus;
import com.ehtesham.securebank.loan.entity.EmiPayment;
import com.ehtesham.securebank.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmiPaymentRepository
        extends JpaRepository<EmiPayment, Long> {

    List<EmiPayment> findByLoanOrderByEmiNumberAsc(Loan loan);
    List<EmiPayment> findByStatusAndDueDateBefore(
            EmiStatus status, LocalDate date);

    long countByLoanAndStatus(Loan loan, EmiStatus status);
}