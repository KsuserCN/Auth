-- Incremental MySQL 8 migration. Safe to rerun; preserves existing SSO clients.
-- This is an account association, not the certified entity name.
-- Displayed name/type are read from the associated users row at authorization time.
-- Existing clients remain unassigned until an administrator verifies their ownership.
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_migrate_sso_client_owner$$
CREATE PROCEDURE ksuser_migrate_sso_client_owner()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'oidc_clients' AND column_name = 'owner_user_id'
  ) THEN
    ALTER TABLE oidc_clients ADD COLUMN owner_user_id BIGINT UNSIGNED DEFAULT NULL
      COMMENT 'SSO应用归属认证账号ID（users.id）' AFTER client_name;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'oidc_clients' AND index_name = 'idx_oidc_clients_owner_user_id'
  ) THEN
    ALTER TABLE oidc_clients ADD INDEX idx_oidc_clients_owner_user_id (owner_user_id);
  END IF;
END$$
CALL ksuser_migrate_sso_client_owner()$$
DROP PROCEDURE ksuser_migrate_sso_client_owner$$
DELIMITER ;

-- Do not assign legacy clients to the executing/current administrator automatically.
-- After verifying the actual account, bind by its UUID using the SQL templates in
-- api/docs/user-verification-entity.md. The account needs verification_entity_name.
