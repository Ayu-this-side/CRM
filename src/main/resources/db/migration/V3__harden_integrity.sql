-- =========================================
-- V3: Phase 1/2 hardening
--   - optimistic locking columns
--   - case-insensitive unique user emails
-- =========================================

ALTER TABLE users     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE leads     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Emails are stored normalised (trimmed/lowercased) by the services, but the
-- index makes case-insensitive uniqueness a database-level guarantee as well.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
