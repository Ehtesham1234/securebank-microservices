-- V12__widen_password_reset_otp_for_multiple_purposes.sql
ALTER TABLE password_reset_otps
    RENAME TO otp_verifications;

ALTER TABLE otp_verifications
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_RESET';

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;