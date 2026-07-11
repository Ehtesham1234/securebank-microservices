package com.ehtesham.securebank.card.repository;

import com.ehtesham.securebank.card.entity.Card;
import com.ehtesham.securebank.common.enums.CardStatus;
import com.ehtesham.securebank.common.enums.CardType;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardRepository
        extends JpaRepository<Card, Long> {

    List<Card> findByUser(User user);

    Optional<Card> findByCardNumber(String cardNumber);

    boolean existsByUserAndCardType(User user, CardType cardType);

    List<Card> findByStatusAndCardType(
            CardStatus status, CardType cardType);
    @Query("SELECT c FROM Card c WHERE c.expiryDate < :today " +
            "AND c.status IN " +
            "(com.ehtesham.securebank.common.enums.CardStatus.ACTIVE, " +
            "com.ehtesham.securebank.common.enums.CardStatus.BLOCKED)")
    List<Card> findExpiredActiveCards(@Param("today") LocalDate today);
}