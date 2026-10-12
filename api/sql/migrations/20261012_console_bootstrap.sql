-- Run only AFTER 20261012_console_official.sql against the intended database.
-- Transactional and rerunnable. The account must already exist in users.
-- A conflicting client ID or missing account aborts without changing roles/apps.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_bootstrap_console$$
CREATE PROCEDURE ksuser_bootstrap_console()
BEGIN
  DECLARE seed_user_id BIGINT UNSIGNED DEFAULT NULL;
  DECLARE existing_id BIGINT UNSIGNED DEFAULT NULL;
  DECLARE matching_seed INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
  SELECT id INTO seed_user_id FROM users
    WHERE uuid = 'e92ca6f9-67ea-4e43-b715-0b37affba178' FOR UPDATE;
  IF seed_user_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Console bootstrap failed: the specified user UUID does not exist';
  END IF;

  SELECT id INTO existing_id FROM official_applications WHERE client_id = 'ksuser_console' FOR UPDATE;
  IF existing_id IS NOT NULL THEN
    SELECT COUNT(*) INTO matching_seed FROM official_applications
      WHERE id = existing_id AND CAST(client_type AS BINARY) = CAST('public' AS BINARY) AND reserved = 1
        AND client_secret_hash IS NULL AND is_active = 1
        AND CAST(CONCAT(';', REPLACE(REPLACE(redirect_uris, CHAR(13), ''), CHAR(10), ';'), ';') AS BINARY)
          LIKE CAST('%;https://console.ksuser.cn/auth/callback;%' AS BINARY)
        AND FIND_IN_SET(CAST('openid' AS BINARY), CAST(REPLACE(scopes, ' ', ',') AS BINARY)) > 0
        AND FIND_IN_SET(CAST('profile' AS BINARY), CAST(REPLACE(scopes, ' ', ',') AS BINARY)) > 0
        AND FIND_IN_SET(CAST('email' AS BINARY), CAST(REPLACE(scopes, ' ', ',') AS BINARY)) > 0;
    IF matching_seed <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Console bootstrap failed: ksuser_console conflicts with an existing application';
    END IF;
  ELSE
    INSERT INTO official_applications
      (client_id, client_name, client_type, redirect_uris, scopes, is_active, reserved)
    VALUES ('ksuser_console', 'Ksuser Console', 'public',
      'https://console.ksuser.cn/auth/callback', 'openid profile email', 1, 1);
  END IF;

  UPDATE users SET system_role = 'SUPER_ADMIN'
    WHERE uuid = 'e92ca6f9-67ea-4e43-b715-0b37affba178';
  COMMIT;
END$$
CALL ksuser_bootstrap_console()$$
DROP PROCEDURE ksuser_bootstrap_console$$
DELIMITER ;

SELECT uuid, username, system_role FROM users
  WHERE uuid = 'e92ca6f9-67ea-4e43-b715-0b37affba178';
SELECT client_id, client_type, redirect_uris, scopes, is_active, reserved
  FROM official_applications WHERE client_id = 'ksuser_console';
