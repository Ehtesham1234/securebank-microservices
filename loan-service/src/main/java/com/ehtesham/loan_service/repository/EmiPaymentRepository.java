package com.ehtesham.loan_service.repository;

import com.ehtesham.loan_service.entity.EmiPayment;
import com.ehtesham.loan_service.entity.Loan;
import com.ehtesham.loan_service.enums.EmiStatus;
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