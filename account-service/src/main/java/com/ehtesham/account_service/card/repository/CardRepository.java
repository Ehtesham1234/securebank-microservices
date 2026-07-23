package com.ehtesham.account_service.card.repository;

import com.ehtesham.account_service.card.entity.Card;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardRepository
        extends JpaRepository<Card, Long> {

    List<Card> findByUserId(Long userId);

    boolean existsByUserIdAndCardType(
            Long userId, CardType cardType);

    List<Card> findByStatusAndCardType(
            CardStatus status, CardType cardType);

    @Query("SELECT c FROM Card c WHERE c.status = 'ACTIVE' " +
            "AND c.expiryDate < :today")
    List<Card> findExpiredActiveCards(@Param("today") LocalDate today);
    Optional<Card> findByUserIdAndCardType(
            Long userId, CardType cardType);

}