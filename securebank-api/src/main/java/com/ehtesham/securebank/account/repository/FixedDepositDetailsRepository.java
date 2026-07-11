package com.ehtesham.securebank.account.repository;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.entity.FixedDepositDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FixedDepositDetailsRepository
        extends JpaRepository<FixedDepositDetails, Long> {

    Optional<FixedDepositDetails> findByAccount(Account account);
}