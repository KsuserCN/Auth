-- Run before deploying website/mobile registration and UnionID application support.
-- This migration replaces the unapplied public-client draft. No intermediate deployment is required.
-- Existing applications retain their client IDs, secrets, callbacks and UnionID capability.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_migrate_oauth_public_pkce$$
CREATE PROCEDURE ksuser_migrate_oauth_public_pkce()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'oauth2_applications' AND column_name = 'client_type') THEN
    ALTER TABLE oauth2_applications ADD COLUMN client_type VARCHAR(32) NOT NULL DEFAULT 'confidential';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'oauth2_applications' AND column_name = 'application_type') THEN
    ALTER TABLE oauth2_applications ADD COLUMN application_type VARCHAR(16) NOT NULL DEFAULT 'website';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'oauth2_applications' AND column_name = 'unionid_status') THEN
    ALTER TABLE oauth2_applications ADD COLUMN unionid_status VARCHAR(16) NOT NULL DEFAULT 'approved';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'oauth2_applications' AND column_name = 'unionid_reason') THEN
    ALTER TABLE oauth2_applications ADD COLUMN unionid_reason VARCHAR(1000) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'oauth2_applications' AND column_name = 'unionid_review_reason') THEN
    ALTER TABLE oauth2_applications ADD COLUMN unionid_review_reason VARCHAR(1000) DEFAULT NULL;
  END IF;
  -- Only the initial ADD backfills approved; reruns never rewrite application states.
  ALTER TABLE oauth2_applications ALTER COLUMN unionid_status SET DEFAULT 'none';
  ALTER TABLE oauth2_applications MODIFY COLUMN app_secret_hash VARCHAR(255) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS oauth2_app_platforms (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_id VARCHAR(64) NOT NULL,
  platform VARCHAR(16) NOT NULL,
  config_json TEXT NOT NULL,
  config_revision VARCHAR(36) NOT NULL,
  is_configured TINYINT(1) NOT NULL DEFAULT 1,
  verification_status VARCHAR(16) NOT NULL DEFAULT 'unverified',
  verification_message VARCHAR(255) DEFAULT NULL,
  verified_at DATETIME(6) DEFAULT NULL,
  last_verification_attempt_at DATETIME(6) DEFAULT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_oauth2_app_platform (app_id, platform),
  CONSTRAINT fk_oauth2_app_platform_app FOREIGN KEY (app_id)
    REFERENCES oauth2_applications(app_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS oauth2_unionid_applications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  review_reason VARCHAR(1000) DEFAULT NULL,
  reviewer_uuid CHAR(36) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  reviewed_at DATETIME(6) DEFAULT NULL,
  KEY idx_unionid_status_created (status, created_at, id),
  KEY idx_unionid_app_created (app_id, created_at, id),
  CONSTRAINT fk_unionid_application_app FOREIGN KEY (app_id)
    REFERENCES oauth2_applications(app_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  -- A legacy grant gets one history row so Console can also revoke it.
  INSERT INTO oauth2_unionid_applications (app_id, status, reason, review_reason, created_at, reviewed_at)
    SELECT a.app_id, 'approved', '历史应用已开通 UnionID', '保留迁移前的 UnionID 能力', a.created_at, CURRENT_TIMESTAMP(6)
    FROM oauth2_applications a
    WHERE a.unionid_status = 'approved'
      AND NOT EXISTS (SELECT 1 FROM oauth2_unionid_applications r WHERE r.app_id = a.app_id);
END$$
CALL ksuser_migrate_oauth_public_pkce()$$
DROP PROCEDURE ksuser_migrate_oauth_public_pkce$$
DELIMITER ;
