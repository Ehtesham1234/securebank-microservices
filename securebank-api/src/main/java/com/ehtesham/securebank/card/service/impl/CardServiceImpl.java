package com.ehtesham.securebank.card.service.impl;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.card.dto.*;
import com.ehtesham.securebank.card.entity.Card;
import com.ehtesham.securebank.card.entity.CreditCardStatement;
import com.ehtesham.securebank.card.repository.CardRepository;
import com.ehtesham.securebank.card.repository.CreditCardStatementRepository;
import com.ehtesham.securebank.card.service.CardService;
import com.ehtesham.securebank.common.enums.*;
import com.ehtesham.securebank.common.exception.*;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class CardServiceImpl implements CardService {

    private static final BigDecimal DEFAULT_DAILY_LIMIT =
            new BigDecimal("50000.00");
    private static final BigDecimal MINIMUM_DUE_PERCENTAGE =
            new BigDecimal("0.05");   // 5% minimum due
    private static final int CREDIT_CARD_INTEREST_RATE_PA = 36;
    private static final int PAYMENT_DUE_DAYS = 15;

    private final CardRepository cardRepository;
    private final CreditCardStatementRepository statementRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public CardServiceImpl(
            CardRepository cardRepository,
            CreditCardStatementRepository statementRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder) {
        this.cardRepository = cardRepository;
        this.statementRepository = statementRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public CardResponse createDebitCard(User user, Account account) {

        String cardNumber = generateCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUser(user);
        card.setAccount(account);
        card.setCardType(CardType.DEBIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(5));
        card.setCvvHash(passwordEncoder.encode(cvv));
        card.setDailyLimit(DEFAULT_DAILY_LIMIT);
        card.setCardHolderName(
                user.getFirstName() + " " + user.getLastName());

        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse issueCreditCard(
            CreditCardRequest request, String adminEmail) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (cardRepository.existsByUserAndCardType(
                user, CardType.CREDIT_CARD)) {
            throw new AccountOperationException(
                    "User already has an active credit card");
        }

        String cardNumber = generateCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUser(user);
        card.setCardType(CardType.CREDIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(3));
        card.setCvvHash(passwordEncoder.encode(cvv));
        card.setCreditLimit(request.getCreditLimit());
        card.setAvailableCredit(request.getCreditLimit());
        card.setOutstandingBill(BigDecimal.ZERO);
        card.setBillingCycleDay(request.getBillingCycleDay());
        card.setCardHolderName(
                user.getFirstName() + " " + user.getLastName());

        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse blockCard(Long cardId, String email) {

        Card card = getCardOwnedByUser(cardId, email);

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new AccountOperationException(
                    "Cannot block a cancelled card");
        }

        card.setStatus(CardStatus.BLOCKED);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse unblockCard(Long cardId, String email) {

        Card card = getCardOwnedByUser(cardId, email);

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
    public CardResponse cancelCard(Long cardId, String adminEmail) {

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Card not found"));

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new AccountOperationException(
                    "Card is already cancelled");
        }

        // for credit card, check outstanding bill before cancellation
        if (card.getCardType() == CardType.CREDIT_CARD
                && card.getOutstandingBill()
                .compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountOperationException(
                    "Cannot cancel credit card with outstanding bill of ₹"
                            + card.getOutstandingBill().toPlainString()
                            + ". Please pay the bill first.");
        }

        card.setStatus(CardStatus.CANCELLED);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getMyCards(String email) {
        User user = getUser(email);
        return cardRepository.findByUser(user)
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

            BigDecimal openingBalance = card.getOutstandingBill();
            BigDecimal totalSpent = card.getCreditLimit()
                    .subtract(card.getAvailableCredit());

            BigDecimal closingBalance = openingBalance.add(totalSpent);

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
            Long cardId, BigDecimal amount, String email) {

        Card card = getCardOwnedByUser(cardId, email);

        if (card.getCardType() != CardType.CREDIT_CARD) {
            throw new AccountOperationException(
                    "Bill payment is only for credit cards");
        }

        if (card.getOutstandingBill().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountOperationException(
                    "No outstanding bill to pay");
        }

        // minimum due = 5% of outstanding, minimum ₹100
        BigDecimal minimumDue = card.getOutstandingBill()
                .multiply(new BigDecimal("0.05"))
                .setScale(4, RoundingMode.HALF_UP)
                .max(new BigDecimal("100.00"));

        if (amount.compareTo(minimumDue) < 0) {
            throw new AccountOperationException(
                    "Minimum payment amount is ₹"
                            + minimumDue.toPlainString()
                            + " (5% of outstanding bill)");
        }

        // cannot pay MORE than what's owed
        BigDecimal actualPayment = amount
                .min(card.getOutstandingBill());

        Account account = accountRepository
                .findByUserAndAccountTypeAndAccountStatus(
                        card.getUser(),
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

        // restore credit limit proportionally
        card.setAvailableCredit(
                card.getAvailableCredit().add(actualPayment));
        card.setOutstandingBill(newOutstanding);

        // if fully paid, clear due date
        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            card.setOutstandingBill(BigDecimal.ZERO);
            card.setAvailableCredit(card.getCreditLimit());
            card.setDueDate(null);
        }

        statementRepository
                .findTopByCardAndPaidFalseOrderByDueDateAsc(card)
                .ifPresent(statement -> {
                    statement.setTotalPaid(
                            statement.getTotalPaid().add(actualPayment));
                    if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
                        statement.setPaid(true);
                    }
                    statementRepository.save(statement);
                });

        cardRepository.save(card);
        return mapToResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatementResponse> getStatements(
            Long cardId, String email) {

        Card card = getCardOwnedByUser(cardId, email);

        return statementRepository
                .findByCardOrderByCreatedAtDesc(card)
                .stream()
                .map(this::mapToStatement)
                .collect(Collectors.toList());
    }

    // CardServiceImpl:
    @Override
    @Transactional
    public CardResponse spend(Long cardId, BigDecimal amount,
                              String description, String email) {

        Card card = getCardOwnedByUser(cardId, email);

        if (card.getCardType() != CardType.CREDIT_CARD) {
            throw new AccountOperationException(
                    "Spend endpoint is only for credit cards");
        }

        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new AccountOperationException(
                    "Card is not active");
        }

        if (card.getExpiryDate().isBefore(LocalDate.now())) {
            throw new AccountOperationException("Card has expired");
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

    private Card getCardOwnedByUser(Long cardId, String email) {
        User user = getUser(email);
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Card not found"));

        if (!card.getUser().getId().equals(user.getId())) {
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
        String number = sb.toString();
        return number.substring(0, 4) + " "
                + number.substring(4, 8) + " "
                + number.substring(8, 12) + " "
                + number.substring(12, 16);
    }

    private String maskCardNumber(String cardNumber) {
        String digits = cardNumber.replace(" ", "");
        return "**** **** **** " + digits.substring(12);
    }

    private String generateCvv() {
        return String.format("%03d", new Random().nextInt(1000));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
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