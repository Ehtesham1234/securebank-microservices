package com.ehtesham.account_service.scheduler;

import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.card.enums.CardStatus;
import com.ehtesham.account_service.card.repository.CardRepository;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.transaction.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class AccountScheduler  {

    private static final Logger log =
            LoggerFactory.getLogger(AccountScheduler .class);

    private final CardRepository cardRepository;
    private final CardService cardService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AccountService accountService;
    public AccountScheduler (
            CardRepository cardRepository,
            CardService cardService,
            IdempotencyKeyRepository idempotencyKeyRepository,
            AccountService accountService) {
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.accountService = accountService;
    }

    // ── Job 1: Generate monthly credit card statements ────────
    // Runs at 1:00 AM every day
    // Each card's billingCycleDay checked inside the method
    @Scheduled(cron = "0 0 1 * * *")
    public void generateCreditCardStatements() {
        log.info("SCHEDULER: Starting credit card " +
                "statement generation");
        try {
            cardService.generateMonthlyStatements();
            log.info("SCHEDULER: Monthly statements generated");
        } catch (Exception e) {
            log.error("SCHEDULER: Statement generation failed: {}",
                    e.getMessage());
        }
    }

    // ── Job 2: Mark expired cards ─────────────────────────────
    // Runs at 3:00 AM every day
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void markExpiredCards() {
        log.info("SCHEDULER: Checking for expired cards");
        try {
            var expiredCards = cardRepository
                    .findExpiredActiveCards(LocalDate.now());

            for (var card : expiredCards) {
                card.setStatus(CardStatus.EXPIRED);
                cardRepository.save(card);
            }

            log.info("SCHEDULER: Marked {} cards as expired",
                    expiredCards.size());
        } catch (Exception e) {
            log.error("SCHEDULER: Card expiry check failed: {}",
                    e.getMessage());
        }
    }
    // ── Job 3: Clean up expired idempotency keys ──────────────
    // Idempotency keys only need to be kept long enough to protect
    // against retries. 24 hours is the industry standard —
    // any client retrying after 24 hours should be treated as
    // a new request, not a duplicate.
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredIdempotencyKeys() {
        log.info("SCHEDULER: Cleaning up expired idempotency keys");
        try {
            LocalDateTime cutoff = LocalDateTime.now()
                    .minusHours(24);
            int deleted = idempotencyKeyRepository
                    .deleteByCreatedAtBefore(cutoff);
            log.info("SCHEDULER: Deleted {} expired " +
                    "idempotency keys", deleted);
        } catch (Exception e) {
            log.error("SCHEDULER: Idempotency cleanup failed: {}",
                    e.getMessage());
        }
    }

    // ── Job 4: Pay out matured Fixed Deposits ─────────────────
    // C9 fix: this job didn't exist at all — FDs had no maturity
    // handling anywhere. Runs at 4:00 AM every day; processMaturedFixed
    // Deposits() is @Transactional at the service layer, and handles
    // each FD in its own try/catch so one bad row can't block the rest.
    @Scheduled(cron = "0 0 4 * * *")
    public void processMaturedFixedDeposits() {
        log.info("SCHEDULER: Checking for matured Fixed Deposits");
        try {
            accountService.processMaturedFixedDeposits();
            log.info("SCHEDULER: Fixed Deposit maturity run complete");
        } catch (Exception e) {
            log.error("SCHEDULER: Fixed Deposit maturity run failed: {}",
                    e.getMessage());
        }
    }
}