package com.ehtesham.securebank.card.service;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.card.dto.*;
import com.ehtesham.securebank.user.entity.User;

import java.math.BigDecimal;
import java.util.List;

public interface CardService {

    // called internally by KycService on account creation
    CardResponse createDebitCard(User user, Account account);

    // admin issues credit card after application
    CardResponse issueCreditCard(
            CreditCardRequest request, String adminEmail);

    CardResponse blockCard(Long cardId, String email);

    CardResponse unblockCard(Long cardId, String email);

    CardResponse cancelCard(Long cardId, String adminEmail);

    List<CardResponse> getMyCards(String email);

    // called by @Scheduled job monthly
    void generateMonthlyStatements();

    // customer pays credit card bill
    CardResponse payCreditCardBill(Long cardId, BigDecimal amount, String email);

    List<StatementResponse> getStatements(
            Long cardId, String email);
    CardResponse spend(Long cardId, BigDecimal amount, String description, String email);
}