-- C3 fix: track wrong guesses against a single OTP so it can be
-- invalidated well before its 10-minute expiry once someone has made too
-- many incorrect attempts against it, instead of remaining brute-forceable
-- for the full lifetime regardless of how many guesses have failed.
ALTER TABLE otp_verifications
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
