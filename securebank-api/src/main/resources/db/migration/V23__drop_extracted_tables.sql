-- Loan service tables (depend on accounts)
DROP TABLE IF EXISTS loan_outbox_events;
DROP TABLE IF EXISTS emi_payments;
DROP TABLE IF EXISTS loans;



-- Other tables
DROP TABLE IF EXISTS credit_card_statements;
DROP TABLE IF EXISTS cards;
DROP TABLE IF EXISTS idempotency_keys;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS fixed_deposit_details;
DROP TABLE IF EXISTS kyc_documents;

-- Now accounts can be dropped safely
DROP TABLE IF EXISTS accounts;