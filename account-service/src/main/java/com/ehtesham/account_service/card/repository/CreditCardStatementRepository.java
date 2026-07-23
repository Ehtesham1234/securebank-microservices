package com.ehtesham.account_service.card.repository;


import com.ehtesham.account_service.card.entity.Card;
import com.ehtesham.account_service.card.entity.CreditCardStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditCardStatementRepository
        extends JpaRepository<CreditCardStatement, Long> {

    List<CreditCardStatement> findByCardOrderByCreatedAtDesc(Card card);

    Optional<CreditCardStatement> findTopByCardAndPaidFalseOrderByDueDateAsc(
            Card card);
}