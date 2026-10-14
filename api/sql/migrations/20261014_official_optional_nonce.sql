-- Add an opt-in compatibility setting for confidential official OIDC clients.
-- Run before deploying the API version that maps allow_missing_nonce.
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_migrate_official_optional_nonce$$
CREATE PROCEDURE ksuser_migrate_official_optional_nonce()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'official_applications'
      AND column_name = 'allow_missing_nonce'
  ) THEN
    ALTER TABLE official_applications ADD COLUMN allow_missing_nonce TINYINT(1)
      NOT NULL DEFAULT 0 AFTER reserved;
  END IF;
END$$
CALL ksuser_migrate_official_optional_nonce()$$
DROP PROCEDURE ksuser_migrate_official_optional_nonce$$
DELIMITER ;
