-- L1 fix: verifyCvv had no rate limiting — a 3-digit CVV is
-- brute-forceable in ~1000 attempts with no lockout if a session is ever
-- compromised without the physical card. Same pattern as
-- LoginAttemptServiceImpl's failed_login_attempts/account_locked_until
-- on User.
ALTER TABLE cards
    ADD COLUMN cvv_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN cvv_locked_until TIMESTAMP NULL;
