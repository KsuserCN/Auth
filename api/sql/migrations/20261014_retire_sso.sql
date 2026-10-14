-- Run only after EzBookKeep is using /official and the old SSO client is deleted.
-- This migration refuses to discard any remaining SSO client registration.
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_check_sso_retirement$$
CREATE PROCEDURE ksuser_check_sso_retirement()
BEGIN
  DECLARE remaining_clients BIGINT DEFAULT 0;
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'oidc_clients'
  ) THEN
    SELECT COUNT(*) INTO remaining_clients FROM oidc_clients;
    IF remaining_clients > 0 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'SSO clients remain; switch and delete them before retiring SSO';
    END IF;
  END IF;
END$$
CALL ksuser_check_sso_retirement()$$
DROP PROCEDURE ksuser_check_sso_retirement$$
DELIMITER ;

DROP TABLE IF EXISTS user_sso_authorizations;
DROP TABLE IF EXISTS oidc_clients;
