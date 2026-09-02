package com.ehtesham.account_service.card.service.impl;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.enums.AccountStatus;
import com.ehtesham.account_service.account.enums.AccountType;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.CvvResponse;
import com.ehtesham.account_service.card.dto.StatementResponse;
import com.ehtesham.account_service.card.entity.Card;
import com.ehtesham.account_service.card.entity.CreditCardStatement;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.enums.CardType;
import com.ehtesham.account_service.card.repository.CardRepository;
import com.ehtesham.account_service.card.repository.CreditCardStatementRepository;
import com.ehtesham.account_service.card.security.CvvService;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.client.InternalUserStatusResponse;
import com.ehtesham.account_service.client.UserSearchClient;
import com.ehtesham.account_service.client.UserStatusCheckUnavailableException;
import com.ehtesham.account_service.client.UserStatusClient;
import com.ehtesham.account_service.exception.AccountOperationException;
import com.ehtesham.account_service.exception.AccountSuspendedException;
import com.ehtesham.account_service.exception.CvvVerificationLockedException;
import com.ehtesham.account_service.exception.InsufficientFundsException;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import com.ehtesham.account_service.security.SecurityUtils;
import com.ehtesham.account_service.transaction.entity.Transaction;
import com.ehtesham.account_service.transaction.enums.TransactionStatus;
import com.ehtesham.account_service.transaction.enums.TransactionType;
import com.ehtesham.account_service.transaction.publisher.TransactionEventPublisher;
import com.ehtesham.account_service.transaction.repository.TransactionRepository;
import com.ehtesham.account_service.transaction.service.impl.IdempotencyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private static final int MAX_CVV_ATTEMPTS = 5;
    private static final long CVV_LOCKOUT_MINUTES = 15;

    // H2 fix: java.util.Random is a predictable PRNG — not appropriate for
    // generating card numbers, which are sensitive identifiers.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CardRepository cardRepository;
    private final CreditCardStatementRepository statementRepository;
    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;
    private final CvvService cvvService;
    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher eventPublisher;
    private final UserStatusClient userStatusClient;
    private final IdempotencyHelper idempotencyHelper;
    private final UserSearchClient userSearchClient;
    // Bug fix: self-injection (via a @Lazy proxy) so
    // generateMonthlyStatements() can call generateStatementForCard()
    // THROUGH Spring's proxy — needed for its
    // @Transactional(REQUIRES_NEW) to actually take effect, the same
    // pattern RefreshTokenServiceImpl already uses for
    // revokeTokenFamily(). Also used by payCreditCardBill() to call
    // payCreditCardBillInternal() as a genuine bean-to-bean call for
    // its own @Transactional to apply correctly under the idempotency
    // wrapper.
    private final CardService self;

    public CardServiceImpl(
            CardRepository cardRepository,
            CreditCardStatementRepository statementRepository,
            AccountRepository accountRepository,
            SecurityUtils securityUtils,
            CvvService cvvService,
            TransactionRepository transactionRepository,
            TransactionEventPublisher eventPublisher,
            UserStatusClient userStatusClient,
            com.ehtesham.account_service.transaction.service.impl.IdempotencyHelper idempotencyHelper, UserSearchClient userSearchClient,
            @org.springframework.context.annotation.Lazy CardService self) {
        this.cardRepository = cardRepository;
        this.statementRepository = statementRepository;
        this.accountRepository = accountRepository;
        this.securityUtils = securityUtils;
        this.cvvService = cvvService;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.userStatusClient = userStatusClient;
        this.idempotencyHelper = idempotencyHelper;
        this.userSearchClient = userSearchClient;
        this.self = self;
    }

    // Bug fix: closes the same "stale JWT status for up to 15 minutes"
    // gap that MoneyMovementExecutor already closes for
    // deposit/withdraw/transfer — this class had no live re-check at
    // all, so a user suspended mid-session could keep spending on
    // credit, viewing/verifying their CVV, or paying down a card bill
    // for the rest of their token's lifetime. Fails open (proceeds on
    // the gateway-verified status) if securebank-api can't be reached.
    private void verifyLiveUserStatus(Long userId) {
        try {
            InternalUserStatusResponse response =
                    userStatusClient.getUser(userId);

            if (!"ACTIVE".equals(response.getUserStatus())) {
                throw new AccountSuspendedException(
                        "Your account status no longer permits this " +
                                "operation. Please contact support.");
            }
        } catch (UserStatusCheckUnavailableException e) {
            log.warn("Live user-status check unavailable for userId={}; " +
                    "proceeding on the gateway-verified status from the " +
                    "request token instead.", userId);
        }
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

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUserId(userId);
        card.setAccount(account);
        card.setCardType(CardType.DEBIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(5));
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

        Card card = new Card();
        card.setCardNumber(cardNumber);
        card.setMaskedNumber(maskCardNumber(cardNumber));
        card.setUserId(userId);
        card.setCardType(CardType.CREDIT_CARD);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(3));
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
    @Transactional(readOnly = true)
    public void generateMonthlyStatements() {
        int today = LocalDate.now().getDayOfMonth();

        // Bug fix: this used to load every eligible card and process
        // them all inside ONE @Transactional method/one Hibernate
        // session. A try/catch around each card looked like it isolated
        // failures, but it didn't: Hibernate defers writes until a
        // flush, and a flush triggered by a LATER card's query (or by
        // the final commit after the loop) can fail outside any
        // per-card try/catch and roll back the WHOLE transaction —
        // silently undoing every statement that appeared to succeed
        // earlier in the same run. Only IDs are collected here
        // (read-only); each card is now processed through
        // generateStatementForCard() below, called via the self-proxy so
        // it runs in its OWN REQUIRES_NEW transaction — a bad card can
        // only ever roll back that one card's statement.
        List<Long> cardIds = cardRepository
                .findByStatusAndCardType(
                        CardStatus.ACTIVE, CardType.CREDIT_CARD)
                .stream()
                .filter(c -> c.getBillingCycleDay() != null
                        && c.getBillingCycleDay() == today)
                .map(Card::getId)
                .collect(Collectors.toList());

        int succeeded = 0;
        for (Long cardId : cardIds) {
            try {
                self.generateStatementForCard(cardId);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to generate statement for cardId={}: {}",
                        cardId, e.getMessage(), e);
            }
        }

        log.info("Monthly statement generation: {}/{} cards succeeded",
                succeeded, cardIds.size());
    }

    @Override
    @Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void generateStatementForCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Card not found: " + cardId));

        // C6 fix: outstandingBill is a live-maintained running ledger —
            // spend() and payCreditCardBill() already keep it as the exact
            // current amount owed, updated in real time as each purchase
            // or payment happens. So it doesn't need anything ADDED to it
            // here; it already *is* the correct closing balance. The old
            // code treated it as a stale "opening balance" and added a
            // (miscalculated) totalSpent on top, which double-counted
            // every cycle's spend into the balance.
            //
            // openingBalance is a historical fact, not something to
            // derive by subtracting cycleSpend back out — that breaks the
            // moment a payment also happened mid-cycle (closingBalance -
            // cycleSpend nets out payments too, not just spend). It's
            // simply whatever the previous statement's closingBalance was.
            BigDecimal openingBalance = statementRepository
                    .findTopByCardOrderByCreatedAtDesc(card)
                    .map(CreditCardStatement::getClosingBalance)
                    .orElse(BigDecimal.ZERO);

            BigDecimal totalSpent = card.getCycleSpend();
            BigDecimal closingBalance = card.getOutstandingBill();
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
            // C6 fix: previously reset to the full creditLimit
            // regardless of unpaid carry-over debt, so a customer who
            // never paid could keep spending up to the full limit again
            // every cycle on top of what they already owed. Available
            // credit must stay bounded by what's actually still owed.
            // (Clamped to zero as a defensive floor — shouldn't go
            // negative given spend() never lets availableCredit run
            // below zero, but a statement calculation is the wrong
            // place to let a rounding edge case surface as a negative
            // credit line.)
            card.setAvailableCredit(
                    card.getCreditLimit()
                            .subtract(closingBalance)
                            .max(BigDecimal.ZERO));
            card.setCycleSpend(BigDecimal.ZERO);
            cardRepository.save(card);
    }

    // Bug fix: unlike deposit/withdraw/transfer (all require an
    // Idempotency-Key and route through IdempotencyHelper), this had no
    // idempotency protection at all — a network-retry double-submit
    // would produce two separately-recorded, fully legitimate-looking
    // WITHDRAW transactions from one intended payment. Mirrors the same
    // claim-first-then-execute pattern TransactionServiceImpl uses:
    // this method itself is deliberately NOT @Transactional (so the
    // idempotency claim commits immediately, before the payment runs —
    // see IdempotencyHelper's own comment on why), and the actual
    // payment logic is called through the self-proxy (payCreditCardBill
    // "Internal") so ITS @Transactional actually takes effect as a
    // genuine bean-to-bean call.
    @Override
    public CardResponse payCreditCardBill(
            Long cardId, BigDecimal amount, String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "CC_BILL_PAYMENT",
                CardResponse.class,
                () -> self.payCreditCardBillInternal(
                        cardId, amount, userId));
    }

    @Override
    @Transactional
    public CardResponse payCreditCardBillInternal(
            Long cardId, BigDecimal amount, Long userId) {

        verifyLiveUserStatus(userId);
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

        // Bug fix: the floor (max(5%, ₹100)) was being compared against
        // the requested amount directly, but the actual debit below is
        // capped to the outstanding bill regardless — so owing, say,
        // ₹50 meant a ₹100 "minimum" was demanded even though ₹50 is
        // the entire balance and nothing larger could ever be charged.
        // The minimum can never sensibly exceed what's actually owed.
        BigDecimal minimumDue = card.getOutstandingBill()
                .multiply(new BigDecimal("0.05"))
                .setScale(4, RoundingMode.HALF_UP)
                .max(new BigDecimal("100.00"))
                .min(card.getOutstandingBill());

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

        BigDecimal newAccountBalance =
                account.getBalance().subtract(actualPayment);
        account.setBalance(newAccountBalance);
        accountRepository.save(account);

        // Bug fix: this debit used to stop here — account.balance moved,
        // but nothing was ever written to the transaction ledger. Every
        // other path that moves money (deposit/withdraw/transfer/EMI
        // debit/FD funding) creates a Transaction row and publishes a
        // balance-update event; this one was missed, which meant credit
        // card bill payments were invisible in transaction history,
        // didn't count toward the account's daily withdrawal limit,
        // never pushed a live balance update over WebSocket, and
        // couldn't be looked up/reversed via reverseTransaction (there
        // was no row to find). Recorded as WITHDRAW so it correctly
        // shows up alongside — and counts toward the running total for
        // — other same-day withdrawals; it is not itself subject to the
        // daily withdrawal cap here, since paying down debt isn't the
        // same risk profile as a cash withdrawal.
        String transactionRef = "CCBILL-" + UUID.randomUUID();
        Transaction transaction = new Transaction();
        transaction.setTransactionRef(transactionRef);
        transaction.setAccount(account);
        transaction.setType(TransactionType.WITHDRAW);
        transaction.setAmount(actualPayment);
        transaction.setBalanceAfter(newAccountBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(
                "Credit card bill payment - card ending "
                        + card.getMaskedNumber());
        transactionRepository.save(transaction);

        eventPublisher.publishTransactionCompleted(
                account.getUserId(),
                account.getAccountNumber(),
                newAccountBalance,
                actualPayment,
                TransactionType.WITHDRAW,
                transactionRef,
                transaction.getDescription());

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
        verifyLiveUserStatus(userId);
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
        // C6 fix: tracked independently so the next statement's
        // "totalSpent" reflects only spend since the last statement,
        // not the whole current outstanding balance.
        card.setCycleSpend(card.getCycleSpend().add(amount));

        return mapToResponse(cardRepository.save(card));
    }

    // Follow-up #5 (Option A): derived on demand from the (decrypted)
    // PAN + expiry — never stored, never logged. card.getCardNumber()
    // returns plaintext transparently via PanEncryptionConverter; it
    // never leaves this method.
    @Override
    @Transactional(readOnly = true)
    public CvvResponse revealCvv(Long cardId) {
        Long userId = securityUtils.getCurrentUserId();
        verifyLiveUserStatus(userId);
        Card card = getCardOwnedByUser(cardId, userId);

        String cvv = cvvService.derive(
                card.getCardNumber(), card.getExpiryDate());

        return CvvResponse.builder().cvv(cvv).build();
    }

    // L1 fix: max wrong guesses before this card's CVV verification
    // locks out for a cooldown period. 1000 possible 3-digit values, so
    // this needs to be tight enough that brute-forcing isn't practical.

    @Override
    @Transactional
    public boolean verifyCvv(Long cardId, String submittedCvv) {
        Long userId = securityUtils.getCurrentUserId();
        verifyLiveUserStatus(userId);
        Card card = getCardOwnedByUser(cardId, userId);

        if (card.getCvvLockedUntil() != null
                && card.getCvvLockedUntil().isAfter(java.time.LocalDateTime.now())) {
            throw new CvvVerificationLockedException(
                    "Too many incorrect CVV attempts. Please try again later.");
        }

        boolean valid = cvvService.verify(
                card.getCardNumber(), card.getExpiryDate(), submittedCvv);

        if (valid) {
            // A correct guess proves the intended cardholder — no
            // reason to keep counting past failures against them.
            card.setCvvFailedAttempts(0);
            card.setCvvLockedUntil(null);
        } else {
            int attempts = card.getCvvFailedAttempts() + 1;
            card.setCvvFailedAttempts(attempts);
            if (attempts >= MAX_CVV_ATTEMPTS) {
                card.setCvvLockedUntil(
                        java.time.LocalDateTime.now().plusMinutes(CVV_LOCKOUT_MINUTES));
                card.setCvvFailedAttempts(0);
            }
        }
        cardRepository.save(card);

        return valid;
    }
    @Override
    @Transactional
    public List<CardResponse> getAllCards(
            Long userId, Long cardId, String maskedNumber, String search) {
        List<Card> cards;
        if (cardId != null) {
            cards = cardRepository.findById(cardId).map(List::of).orElseGet(List::of);
        } else if (userId != null) {
            cards = cardRepository.findByUserId(userId);
        } else if (maskedNumber != null && !maskedNumber.isBlank()) {
            cards = cardRepository.findByMaskedNumberContainingIgnoreCase(maskedNumber.trim());
        } else if (search != null && !search.isBlank()) {
            List<Long> userIds = userSearchClient.searchUserIds(search.trim());
            cards = userIds.isEmpty() ? List.of() : cardRepository.findByUserIdIn(userIds);
        } else {
            cards = cardRepository.findAll();
        }
        return cards.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // Deliberately separate from blockCard()/unblockCard() above rather than
    // reusing them — those go through getCardOwnedByUser(cardId, userId),
    // which scopes the lookup to the CALLING user's own cards and would
    // reject an admin acting on someone else's. This mirrors the existing
    // cancelCard()'s plain findById(cardId), just for the reversible
    // BLOCKED status instead of the permanent CANCELLED one.
    @Override
    @Transactional
    public CardResponse adminBlockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new AccountOperationException("Cannot block a cancelled card");
        }

        card.setStatus(CardStatus.BLOCKED);
        return mapToResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public CardResponse adminUnblockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new AccountOperationException("Only BLOCKED cards can be unblocked");
        }

        if (card.getExpiryDate().isBefore(LocalDate.now())) {
            throw new AccountOperationException("Cannot unblock an expired card");
        }

        card.setStatus(CardStatus.ACTIVE);
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

    // NOTE: this does not compute/append a real Luhn check digit — fine
    // for an internal simulated bank where these numbers are never run
    // through a real card network, but don't mistake this for validation
    // logic if it's ever reused somewhere that matters.
    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder("4");
        for (int i = 0; i < 15; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
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

    private CardResponse mapToResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .userId(card.getUserId())
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