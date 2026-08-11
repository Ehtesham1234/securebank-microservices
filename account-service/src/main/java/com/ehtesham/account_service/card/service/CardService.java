package com.ehtesham.account_service.card.service;

import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.CvvResponse;
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
    CardResponse payCreditCardBill(
            Long cardId, BigDecimal amount, String idempotencyKey);

    // Bug fix: internal step of payCreditCardBill's idempotency wrapper
    // — public (not private) and called through the self-proxy
    // specifically so its own @Transactional takes effect as a genuine
    // bean-to-bean call, same reasoning as generateStatementForCard().
    CardResponse payCreditCardBillInternal(
            Long cardId, BigDecimal amount, Long userId);
    List<StatementResponse> getStatements(Long cardId);
    CardResponse spend(Long cardId, BigDecimal amount,
                       String description);

    // Follow-up #5 (Option A): CVV is derived on demand, never stored.
    // revealCvv is for the cardholder viewing their own card (the same
    // trust level as viewing the card itself); verifyCvv is for a future
    // payment/checkout flow to confirm a submitted CVV without ever
    // exposing the real value on a mismatch.
    CvvResponse revealCvv(Long cardId);
    boolean verifyCvv(Long cardId, String submittedCvv);

    // Scheduler job — no user context needed
    void generateMonthlyStatements();

    // Bug fix: extracted so generateMonthlyStatements() can process each
    // card in its OWN transaction (via a self-injected proxy call — see
    // the implementation) instead of one shared transaction/persistence
    // context for the whole batch, where a single bad card's failure
    // could roll back every card already processed in that run.
    void generateStatementForCard(Long cardId);
}