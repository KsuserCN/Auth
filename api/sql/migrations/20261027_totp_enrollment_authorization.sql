-- Run before deploying the TOTP enrollment authorization API on every API instance.
-- Existing enabled TOTP credentials and recovery codes are preserved.
CREATE TABLE IF NOT EXISTS totp_enrollments (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  session_id BIGINT UNSIGNED NOT NULL,
  session_version INT NOT NULL,
  pending_secret_ciphertext VARBINARY(512) DEFAULT NULL,
  recovery_codes_ciphertext VARBINARY(512) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  completed_at DATETIME(6) DEFAULT NULL,
  cancelled_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_totp_enrollment_user (user_id),
  KEY idx_totp_enrollment_expiry (expires_at),
  CONSTRAINT fk_totp_enrollment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_totp_enrollment_session FOREIGN KEY (session_id) REFERENCES user_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- These pending secrets had no independent, session-bound authorization.
UPDATE user_totp
SET pending_secret_ciphertext = NULL, pending_expires_at = NULL
WHERE is_enabled = 0;
