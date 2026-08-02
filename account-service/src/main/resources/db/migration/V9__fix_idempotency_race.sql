-- C6 fix, part 1: the old constraint (idempotency_key, user_id) didn't
-- match the app-level lookup key (idempotency_key, user_id, operation_type)
-- — reusing the same key for two different operation types slipped past
-- the app-level check and only failed at insert time, after the second
-- operation had already run. Widen it to match.
ALTER TABLE idempotency_keys DROP INDEX uq_idempotency_key_user;
ALTER TABLE idempotency_keys
    ADD CONSTRAINT uq_idempotency_key_user_op
        UNIQUE (idempotency_key, user_id, operation_type);

-- C6 fix, part 2: status lets IdempotencyHelper tell "claimed, still
-- running" apart from "finished, here's the cached result" — the row is
-- now inserted BEFORE the operation runs (the claim), so concurrent
-- requests hit the unique constraint immediately instead of both slipping
-- through a since-stale "not found yet" check.
ALTER TABLE idempotency_keys
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';

-- Any pre-existing rows already have a response_body, so they're finished.
UPDATE idempotency_keys SET status = 'COMPLETED' WHERE response_body IS NOT NULL;
