ALTER TABLE idempotency_keys
DROP INDEX idempotency_key;

ALTER TABLE idempotency_keys
    ADD UNIQUE KEY uq_idempotency_key_user (idempotency_key, user_id);