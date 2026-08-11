-- C9 fix: nothing previously tracked whether a Fixed Deposit had been
-- paid out. Every FD needs a status so the new maturity job can find
-- ACTIVE ones past their maturityDate and never reprocess a MATURED one.
ALTER TABLE fixed_deposit_details
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
