-- Bug fix: createNextPendingInstallment() (added in the loan-delinquency
-- fix) had no existence check before inserting, and is reachable twice
-- for the same installment via a redelivered Kafka message
-- (LoanSagaConsumer's @KafkaListener, standard at-least-once delivery)
-- or a client retry through payEmi(). The application-level check added
-- alongside this migration closes that for the normal path; this
-- constraint is the backstop so a duplicate can never land in the table
-- even if some future code path reintroduces the same mistake — it
-- fails fast with a DataIntegrityViolationException (already mapped to
-- a clean 409 by GlobalExceptionHandler) instead of silently duplicating
-- a row that later breaks findByLoanAndEmiNumber() everywhere it's used.
ALTER TABLE emi_payments
    ADD CONSTRAINT uq_emi_payments_loan_emi_number
        UNIQUE (loan_id, emi_number);
