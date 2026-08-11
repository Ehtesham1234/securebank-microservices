package com.ehtesham.account_service.account.repository;


import com.ehtesham.account_service.account.entity.FixedDepositDetails;
import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.enums.FdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedDepositDetailsRepository
        extends JpaRepository<FixedDepositDetails, Long> {

    Optional<FixedDepositDetails> findByAccount(Account account);

    // C9 fix: backs the maturity scheduler — every ACTIVE FD whose
    // maturityDate has arrived (or passed, in case a run was ever
    // missed) is due for payout.
    List<FixedDepositDetails> findByStatusAndMaturityDateLessThanEqual(
            FdStatus status, LocalDate date);
}