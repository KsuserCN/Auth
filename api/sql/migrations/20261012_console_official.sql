-- Incremental MySQL 8 migration. Safe to rerun; never rebuilds existing tables.
-- Select the existing Ksuser database before running this file.
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS ksuser_migrate_console_role$$
CREATE PROCEDURE ksuser_migrate_console_role()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'system_role'
  ) THEN
    ALTER TABLE users ADD COLUMN system_role ENUM('USER','ADMIN','SUPER_ADMIN')
      NOT NULL DEFAULT 'USER' COMMENT '系统角色，与开发者认证状态分离' AFTER verification_type;
  END IF;
  UPDATE users SET system_role = 'ADMIN'
    WHERE verification_type = 'admin' AND system_role = 'USER';
  UPDATE users SET verification_type = 'none' WHERE verification_type = 'admin';
END$$
CALL ksuser_migrate_console_role()$$
DROP PROCEDURE ksuser_migrate_console_role$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS official_applications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  client_secret_hash VARCHAR(255) DEFAULT NULL,
  client_name VARCHAR(120) NOT NULL,
  logo_url VARCHAR(500) DEFAULT NULL,
  client_type VARCHAR(32) NOT NULL,
  redirect_uris TEXT NOT NULL COMMENT '精确回调地址，多个以换行或分号分隔',
  scopes VARCHAR(255) NOT NULL DEFAULT 'openid',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  reserved TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_official_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='仅由超级管理员配置的官方应用，与 OAuth/SSO 开发者应用独立';

CREATE TABLE IF NOT EXISTS user_official_authorizations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  client_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  scopes VARCHAR(255) NOT NULL DEFAULT 'openid',
  redirect_uri VARCHAR(2048) NOT NULL,
  authorized_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_authorized_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_official_grant (user_id, client_id),
  KEY idx_official_grants_user (user_id, last_authorized_at),
  CONSTRAINT fk_official_grant_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_official_grant_app FOREIGN KEY (client_id) REFERENCES official_applications(client_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户授予官方应用的持久授权，删除记录立即撤销令牌与 Console 会话';

CREATE TABLE IF NOT EXISTS console_audit_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  target VARCHAR(128) NOT NULL,
  action VARCHAR(64) NOT NULL,
  details TEXT NOT NULL COMMENT '不包含密钥、令牌或认证凭据',
  ip_address VARCHAR(45) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_console_audit_time (created_at),
  KEY idx_console_audit_actor (actor_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Console 管理操作审计，保留历史操作者 UUID';
