-- C5 fix, part 1: a CVV/CVV2 must never be stored past the moment of
-- authorization — not in plaintext, not hashed, not encrypted. PCI-DSS
-- requirement 3.2 prohibits retaining it in any form. It was only ever
-- used once (at issuance) in this codebase, so there's nothing that
-- depended on persisting it.
ALTER TABLE cards DROP COLUMN cvv_hash;

-- C5 fix, part 2: card_number (the PAN) was stored in plaintext. It's now
-- encrypted at rest with AES-256-GCM (see PanEncryptionConverter), so the
-- column needs room for ciphertext + IV + auth tag, base64-encoded — a
-- fixed 19-char plaintext no longer fits. The unique index is dropped:
-- with a random IV per encryption, the same PAN produces different
-- ciphertext each time, so a DB-level uniqueness check on the encrypted
-- value can no longer do meaningful duplicate detection.
--
-- NOTE: this migration does not backfill/re-encrypt any pre-existing
-- plaintext rows — if this is ever run against a database that already
-- has real card data in it (rather than a fresh dev/demo setup), write a
-- one-off backfill job first that reads each row, encrypts it with
-- PanEncryptionConverter, and writes it back before applying this.
ALTER TABLE cards DROP INDEX card_number;
ALTER TABLE cards MODIFY COLUMN card_number VARCHAR(255) NOT NULL;
