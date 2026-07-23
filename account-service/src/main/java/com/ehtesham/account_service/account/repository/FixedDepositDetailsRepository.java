package com.ehtesham.account_service.account.repository;


import com.ehtesham.account_service.account.entity.FixedDepositDetails;
import com.ehtesham.account_service.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FixedDepositDetailsRepository
        extends JpaRepository<FixedDepositDetails, Long> {

    Optional<FixedDepositDetails> findByAccount(Account account);
}