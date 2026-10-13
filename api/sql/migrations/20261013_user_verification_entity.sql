-- Incremental MySQL 8 migration. Safe to rerun; preserves existing certifications.
-- Apply 20261012_console_official.sql first to separate system roles from certification.
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_migrate_verification_entity$$
CREATE PROCEDURE ksuser_migrate_verification_entity()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'verification_entity_name'
  ) THEN
    ALTER TABLE users ADD COLUMN verification_entity_name VARCHAR(200) DEFAULT NULL
      COMMENT '认证实体：个人及管理员真实姓名、企业名称，仅管理员通过数据库维护' AFTER verification_type;
  END IF;
END$$
CALL ksuser_migrate_verification_entity()$$
DROP PROCEDURE ksuser_migrate_verification_entity$$
DELIMITER ;

-- Existing verified users require administrator review and manual name entry.
-- Do not copy username or real_name: both are editable by the account holder.
