package com.ehtesham.securebank.card.repository;

import com.ehtesham.securebank.card.entity.Card;
import com.ehtesham.securebank.card.entity.CreditCardStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditCardStatementRepository
        extends JpaRepository<CreditCardStatement, Long> {

    List<CreditCardStatement> findByCardOrderByCreatedAtDesc(Card card);

    Optional<CreditCardStatement> findTopByCardAndPaidFalseOrderByDueDateAsc(
            Card card);
}