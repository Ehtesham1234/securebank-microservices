package com.ehtesham.account_service.card.service;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.StatementResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CardService {

    // Called internally by kyc-service endpoint
    CardResponse createDebitCard(Long userId,
                                 String cardHolderName, Account account);

    // Admin issues credit card
    CardResponse issueCreditCard(CreditCardRequest request);

    // userId from SecurityContext — no email param
    CardResponse blockCard(Long cardId);
    CardResponse unblockCard(Long cardId);
    CardResponse cancelCard(Long cardId);
    List<CardResponse> getMyCards();
    CardResponse payCreditCardBill(Long cardId, BigDecimal amount);
    List<StatementResponse> getStatements(Long cardId);
    CardResponse spend(Long cardId, BigDecimal amount,
                       String description);

    // Scheduler job — no user context needed
    void generateMonthlyStatements();
}