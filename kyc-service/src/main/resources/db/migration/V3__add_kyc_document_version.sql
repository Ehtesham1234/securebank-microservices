-- Bug fix: KycDocument had no optimistic-locking column, unlike every
-- other staff-decision entity in this codebase (loans, accounts, cards).
-- Two tellers acting on the same PENDING submission near-simultaneously
-- (one verify, one reject) could both pass the status check and silently
-- overwrite each other with no error. Default 0 for existing rows.
ALTER TABLE kyc_documents
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
