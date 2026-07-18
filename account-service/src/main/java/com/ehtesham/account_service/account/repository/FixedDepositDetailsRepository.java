package com.ehtesham.account_service.repository;


import com.ehtesham.account_service.entity.FixedDepositDetails;
import com.ehtesham.account_service.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FixedDepositDetailsRepository
        extends JpaRepository<FixedDepositDetails, Long> {

    Optional<FixedDepositDetails> findByAccount(Account account);
}