-- Checked against the existing Auth database on 2026-10-04 (MySQL 8.0.46).
-- Select Auth in your SQL client, back up, and apply ONCE before the new JAR.
-- Existing provider is VARCHAR(32): it already supports 'apple'. Do not narrow it
-- to ENUM. users.email and users.password_hash already accept NULL.
-- No user rows, existing indexes, or foreign keys are modified.
ALTER TABLE user_oauth_accounts
  ADD COLUMN apple_client_id VARCHAR(255) DEFAULT NULL,
  ADD COLUMN apple_refresh_token_encrypted TEXT DEFAULT NULL,
  ADD COLUMN apple_email_forwarding_enabled TINYINT(1) DEFAULT NULL,
  ALGORITHM=INSTANT;

-- Deliberately no users FK: deletion must not remove pending provider revocations.
CREATE TABLE IF NOT EXISTS apple_revocation_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  client_id VARCHAR(255) NOT NULL,
  encrypted_token TEXT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NOT NULL,
  KEY idx_apple_revocations_due (next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
