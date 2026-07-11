ALTER TABLE idempotency_keys
    ADD COLUMN operation_type VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';