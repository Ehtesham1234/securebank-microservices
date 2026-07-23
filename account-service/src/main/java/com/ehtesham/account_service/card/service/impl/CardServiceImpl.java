package com.ehtesham.account_service.card.service.impl;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.enums.AccountStatus;
import com.ehtesham.account_service.account.enums.AccountType;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.impl.AccountServiceImpl;
import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.StatementResponse;
import com.ehtesham.account_service.card.entity.Card;
import com.ehtesham.account_service.card.entity.CreditCardStatement;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import com.ehtesham.account_service.card.repository.CardRepository;
import com.ehtesham.account_service.card.repository.CreditCardStatementRepository;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.exception.AccountOperationException;
import com.ehtesham.account_service.exception.InsufficientFundsException;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import com.ehtesham.account_service.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class CardServiceImpl implements CardService {
    private static final Logger log =
            LoggerFactory.getLogger(CardServiceImpl.class);
    private static final BigDecimal DEFAULT_DAILY_LIMIT =
            new BigDecimal("50000.00");
    private static final BigDecimal MINIMUM_DUE_PERCENTAGE =
            new BigDecimal("0.05");
    private static final int PAYMENT_DUE_DAYS = 15;

    private final CardRepository cardRepository;
    private final CreditCardStatementRepository statementRepository;
    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;
    private final PasswordEncoder passwordEncoder;

    public CardServiceImpl(
            CardRepository cardRepository,
            CreditCardStatementRepository statementRepository,
            AccountRepository accountRepository,
            SecurityUtils securityUtils) {
        this.cardRepository = cardRepository;
        this.statementRepository = statementRepository;
        this.accountRepository = accountRepository;
        this.securityUtils = securityUtils;
        // BCrypt for CVV hashing — no Spring Security UserDetails here
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Called by kyc-service via internal endpoint.
     * cardHolderName passed directly — no User entity needed.
     */
    @Override
    @Transactional
    public CardResponse createDebitCard(Long userId,
                                        String cardHolderName, Account account) {
        // Idempotent — return existing debit card if present
        Optional<Card> existing = cardRepository
                .findByUserIdAndCardType(userId, CardType.DEBIT_CARD);

        if (existing.isPresent()) {
            log.info("Debit card already exists for userId={}, " +
                    "returning existing", userId);
            return mapToResponse(existing.get());
        }
        String cardNumber = generateCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUserId(userId);
        card.setAccount(account);
        card.setCardType(CardType.DEBIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(5));
        card.setCvvHash(passwordEncoder.encode(cvv));
        card.setDailyLimit(DEFAULT_DAILY_LIMIT);
        card.setCardHolderName(cardHolderName);

        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse issueCreditCard(
            CreditCardRequest request) {

        Long userId = request.getUserId();

        if (cardRepository.existsByUserIdAndCardType(
                userId, CardType.CREDIT_CARD)) {
            throw new AccountOperationException(
                    "User already has an active credit card");
        }

        String cardNumber = generateCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUserId(userId);
        card.setCardType(CardType.CREDIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(3));
        card.setCvvHash(passwordEncoder.encode(cvv));
        card.setCreditLimit(request.getCreditLimit());
        card.setAvailableCredit(request.getCreditLimit());
        card.setOutstandingBill(BigDecimal.ZERO);
        card.setBillingCycleDay(request.getBillingCycleDay());
        card.setCardHolderName(request.getCardHolderName());

        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse blockCard(Long cardId) {
        Long userId = securityUtils.getCurrentUserId();
        Card card = getCardOwnedByUser(cardId, userId);

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new AccountOperationException(
                    "Cannot block a cancelled card");
        }

        card.setStatus(CardStatus.BLOCKED);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse unblockCard(Long cardId) {
        Long userId = securityUtils.getCurrentUserId();
        Card card = getCardOwnedByUser(cardId, userId);

        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new AccountOperationException(
                    "Only BLOCKED cards can be unblocked");
        }

        if (card.getExpiryDate().isBefore(LocalDate.now())) {
            throw new AccountOperationException(
                    "Cannot unblock an expired card");
        }

        card.setStatus(CardStatus.ACTIVE);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse cancelCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Card not found"));

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new AccountOperationException(
                    "Card is already cancelled");
        }

        if (card.getCardType() == CardType.CREDIT_CARD
                && card.getOutstandingBill()
                .compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountOperationException(
                    "Cannot cancel credit card with " +
                            "outstanding bill of ₹"
                            + card.getOutstandingBill().toPlainString()
                            + ". Please pay the bill first.");
        }

        card.setStatus(CardStatus.CANCELLED);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getMyCards() {
        Long userId = securityUtils.getCurrentUserId();
        return cardRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void generateMonthlyStatements() {
        int today = LocalDate.now().getDayOfMonth();

        List<Card> cards = cardRepository
                .findByStatusAndCardType(
                        CardStatus.ACTIVE, CardType.CREDIT_CARD)
                .stream()
                .filter(c -> c.getBillingCycleDay() != null
                        && c.getBillingCycleDay() == today)
                .collect(Collectors.toList());

        for (Card card : cards) {
            BigDecimal openingBalance =
                    card.getOutstandingBill();
            BigDecimal totalSpent = card.getCreditLimit()
                    .subtract(card.getAvailableCredit());
            BigDecimal closingBalance =
                    openingBalance.add(totalSpent);
            BigDecimal minimumDue = closingBalance
                    .multiply(MINIMUM_DUE_PERCENTAGE)
                    .setScale(4, RoundingMode.HALF_UP);
            LocalDate dueDate = LocalDate.now()
                    .plusDays(PAYMENT_DUE_DAYS);

            CreditCardStatement statement =
                    new CreditCardStatement();
            statement.setCard(card);
            statement.setBillingPeriodStart(
                    LocalDate.now().minusMonths(1));
            statement.setBillingPeriodEnd(LocalDate.now());
            statement.setTotalSpent(totalSpent);
            statement.setOpeningBalance(openingBalance);
            statement.setClosingBalance(closingBalance);
            statement.setMinimumDue(minimumDue);
            statement.setDueDate(dueDate);
            statement.setPaid(false);
            statementRepository.save(statement);

            card.setOutstandingBill(closingBalance);
            card.setDueDate(dueDate);
            card.setAvailableCredit(card.getCreditLimit());
            cardRepository.save(card);
        }
    }

    @Override
    @Transactional
    public CardResponse payCreditCardBill(
            Long cardId, BigDecimal amount) {

        Long userId = securityUtils.getCurrentUserId();
        Card card = getCardOwnedByUser(cardId, userId);

        if (card.getCardType() != CardType.CREDIT_CARD) {
            throw new AccountOperationException(
                    "Bill payment is only for credit cards");
        }

        if (card.getOutstandingBill()
                .compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountOperationException(
                    "No outstanding bill to pay");
        }

        BigDecimal minimumDue = card.getOutstandingBill()
                .multiply(new BigDecimal("0.05"))
                .setScale(4, RoundingMode.HALF_UP)
                .max(new BigDecimal("100.00"));

        if (amount.compareTo(minimumDue) < 0) {
            throw new AccountOperationException(
                    "Minimum payment amount is ₹"
                            + minimumDue.toPlainString());
        }

        BigDecimal actualPayment = amount
                .min(card.getOutstandingBill());

        // Use userId-based lookup — no User entity
        Account account = accountRepository
                .findByUserIdAndAccountTypeAndAccountStatus(
                        userId,
                        AccountType.SAVINGS,
                        AccountStatus.ACTIVE)
                .orElseThrow(() ->
                        new AccountOperationException(
                                "No active savings account found"));

        if (account.getBalance().compareTo(actualPayment) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance to pay ₹"
                            + actualPayment.toPlainString()
                            + ". Available: ₹"
                            + account.getBalance().toPlainString());
        }

        account.setBalance(
                account.getBalance().subtract(actualPayment));
        accountRepository.save(account);

        BigDecimal newOutstanding = card.getOutstandingBill()
                .subtract(actualPayment)
                .setScale(4, RoundingMode.HALF_UP);

        card.setAvailableCredit(
                card.getAvailableCredit().add(actualPayment));
        card.setOutstandingBill(newOutstanding);

        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            card.setOutstandingBill(BigDecimal.ZERO);
            card.setAvailableCredit(card.getCreditLimit());
            card.setDueDate(null);
        }

        statementRepository
                .findTopByCardAndPaidFalseOrderByDueDateAsc(card)
                .ifPresent(s -> {
                    s.setTotalPaid(s.getTotalPaid()
                            .add(actualPayment));
                    if (newOutstanding.compareTo(
                            BigDecimal.ZERO) <= 0) {
                        s.setPaid(true);
                    }
                    statementRepository.save(s);
                });

        cardRepository.save(card);
        return mapToResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatementResponse> getStatements(Long cardId) {
        Long userId = securityUtils.getCurrentUserId();
        Card card = getCardOwnedByUser(cardId, userId);
        return statementRepository
                .findByCardOrderByCreatedAtDesc(card)
                .stream()
                .map(this::mapToStatement)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CardResponse spend(Long cardId, BigDecimal amount,
                              String description) {

        Long userId = securityUtils.getCurrentUserId();
        Card card = getCardOwnedByUser(cardId, userId);

        if (card.getCardType() != CardType.CREDIT_CARD) {
            throw new AccountOperationException(
                    "Spend endpoint is only for credit cards");
        }
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new AccountOperationException(
                    "Card is not active");
        }
        if (card.getExpiryDate().isBefore(LocalDate.now())) {
            throw new AccountOperationException(
                    "Card has expired");
        }
        if (card.getAvailableCredit().compareTo(amount) < 0) {
            throw new AccountOperationException(
                    "Insufficient credit limit. Available: ₹"
                            + card.getAvailableCredit().toPlainString());
        }

        card.setAvailableCredit(
                card.getAvailableCredit().subtract(amount));
        card.setOutstandingBill(
                card.getOutstandingBill().add(amount));

        return mapToResponse(cardRepository.save(card));
    }

    // ── Private helpers ───────────────────────────────────────────

    private Card getCardOwnedByUser(Long cardId, Long userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Card not found"));

        if (!card.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Card not found");
        }
        return card;
    }

    private String generateCardNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("4");
        for (int i = 0; i < 15; i++) {
            sb.append(random.nextInt(10));
        }
        String n = sb.toString();
        return n.substring(0, 4) + " " + n.substring(4, 8)
                + " " + n.substring(8, 12) + " "
                + n.substring(12, 16);
    }

    private String maskCardNumber(String cardNumber) {
        String d = cardNumber.replace(" ", "");
        return "**** **** **** " + d.substring(12);
    }

    private String generateCvv() {
        return String.format("%03d",
                new Random().nextInt(1000));
    }

    private CardResponse mapToResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .maskedNumber(card.getMaskedNumber())
                .cardType(card.getCardType())
                .status(card.getStatus())
                .expiryDate(card.getExpiryDate())
                .cardHolderName(card.getCardHolderName())
                .dailyLimit(card.getDailyLimit())
                .creditLimit(card.getCreditLimit())
                .availableCredit(card.getAvailableCredit())
                .outstandingBill(card.getOutstandingBill())
                .dueDate(card.getDueDate())
                .accountNumber(card.getAccount() != null
                        ? card.getAccount().getAccountNumber()
                        : null)
                .createdAt(card.getCreatedAt())
                .build();
    }

    private StatementResponse mapToStatement(
            CreditCardStatement s) {
        return StatementResponse.builder()
                .id(s.getId())
                .billingPeriodStart(s.getBillingPeriodStart())
                .billingPeriodEnd(s.getBillingPeriodEnd())
                .totalSpent(s.getTotalSpent())
                .totalPaid(s.getTotalPaid())
                .openingBalance(s.getOpeningBalance())
                .closingBalance(s.getClosingBalance())
                .minimumDue(s.getMinimumDue())
                .dueDate(s.getDueDate())
                .paid(s.isPaid())
                .createdAt(s.getCreatedAt())
                .build();
    }
}