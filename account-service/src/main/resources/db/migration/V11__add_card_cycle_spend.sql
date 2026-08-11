-- C6 fix: generateMonthlyStatements() computed "this cycle's spend" as
-- (credit_limit - available_credit), which is always equal to the
-- CURRENT outstanding_bill (spend()/payCreditCardBill() keep
-- available_credit == credit_limit - outstanding_bill at all times) —
-- so closing_balance = opening_balance + that expression was silently
-- double-counting the outstanding bill on every statement.
--
-- cycle_spend tracks new spend since the last statement independently
-- of outstanding_bill/available_credit, so it survives partial payments
-- and carries over debt correctly. Existing cards start at 0 — spend
-- accrued before this fix is already reflected in their outstanding_bill,
-- and there's no reliable way to reconstruct "spend since last statement"
-- retroactively, so this simply starts counting forward from here.
ALTER TABLE cards
    ADD COLUMN cycle_spend DECIMAL(19,4) NOT NULL DEFAULT 0;
