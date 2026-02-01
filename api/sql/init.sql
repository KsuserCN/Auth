-- ===========================
-- 终极版：users + user_sessions
-- 特色：
-- 1) users.uuid 使用 ascii_bin，严格大小写匹配
-- 3) user_sessions.refresh_token_verifier 唯一索引，防重复会话/重放插入
-- ===========================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------
-- users：内部ID + 对外UUID(sub)
-- ---------------------------
CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键ID（数据库内部使用，外键引用它）',

  uuid CHAR(36)
    CHARACTER SET ascii
    COLLATE ascii_bin
    NOT NULL
    COMMENT '公开用户ID(sub)，OAuth返回给第三方使用（严格大小写匹配）',

  username VARCHAR(50) NOT NULL COMMENT '登录用户名（唯一）',
  email VARCHAR(255) DEFAULT NULL COMMENT '邮箱（可选，用于找回密码；允许多个NULL）',

  password_hash VARCHAR(255) DEFAULT NULL COMMENT '加盐后的密码哈希（纯Passkey用户可为空）',
  avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (id),
  UNIQUE KEY uk_users_uuid (uuid) COMMENT '唯一约束：对外公开UUID(sub)不可重复',
  UNIQUE KEY uk_users_username (username) COMMENT '唯一约束：用户名不可重复',
  UNIQUE KEY uk_users_email (email) COMMENT '唯一约束：邮箱不可重复（NULL可重复）'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户表：内部使用自增ID，对外使用UUID(sub)';


-- ---------------------------
-- user_sessions：刷新令牌会话表（极简，仅登录态）
-- ---------------------------
CREATE TABLE user_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
    COMMENT '会话表主键ID（仅用于数据库内部定位）',

  user_id BIGINT UNSIGNED NOT NULL
    COMMENT '关联 users.id 的内部用户ID（外键字段）',

  refresh_token_verifier VARBINARY(255) NOT NULL
    COMMENT 'Refresh Token 的校验值（hash/verifier），不存明文 token',

  verifier_algo VARCHAR(16) NOT NULL DEFAULT 'argon2id'
    COMMENT 'refresh_token_verifier 使用的算法标识（如 argon2id / bcrypt 等）',

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '会话创建时间（首次登录或签发 refresh token 的时间）',

  expires_at DATETIME NOT NULL
    COMMENT '会话过期时间（超过该时间 refresh token 必须失效）',

  revoked_at DATETIME NULL
    COMMENT '会话主动失效时间（登出/踢下线时设置，NULL 表示仍有效）',

  PRIMARY KEY (id),

  CONSTRAINT fk_user_sessions_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户登录会话表：仅用于 Refresh Token 生命周期管理（极简）';


-- ---------------------------
-- 索引 1：按用户查有效会话
-- 条件一般是：user_id = ? AND revoked_at IS NULL AND expires_at > NOW()
-- ---------------------------
CREATE INDEX idx_user_sessions_user_active
  ON user_sessions (user_id, revoked_at, expires_at);


-- ---------------------------
-- 索引 2（采纳方案 3）：refresh verifier 唯一约束
-- 防止同一个 refresh token verifier 被重复插入（例如重放/重试导致重复会话）
-- ---------------------------
CREATE UNIQUE INDEX uk_user_sessions_refresh_verifier
  ON user_sessions (refresh_token_verifier);


-- ==========================================================
-- Passkey（WebAuthn）最小化 MySQL 方案：只保留 1 张表
-- 说明：
--   - challenge（注册/登录/敏感）全部迁移到 Redis（TTL + 用完即删）
--   - MySQL 只长期保存 Passkey 凭证（公钥/credential_id/sign_count等）
-- ==========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 如需彻底清理历史表（可选：你以前建过就删掉，没建过也不报错）
DROP TABLE IF EXISTS passkey_sensitive_verifications;
DROP TABLE IF EXISTS passkey_authentication_challenges;
DROP TABLE IF EXISTS passkey_registration_challenges;

-- 只保留这一张
DROP TABLE IF EXISTS user_passkeys;

CREATE TABLE user_passkeys (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Passkey凭证主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '关联用户ID（users.id）',

  credential_id VARBINARY(512) NOT NULL COMMENT 'WebAuthn credential ID（二进制）',
  public_key_cose VARBINARY(1024) NOT NULL COMMENT '公钥（COSE Key 二进制）',

  sign_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'WebAuthn signCount（计数器）',

  transports VARCHAR(255) DEFAULT NULL COMMENT 'usb,nfc,ble,internal等（逗号分隔，可后续改JSON）',
  aaguid BINARY(16) DEFAULT NULL COMMENT '认证器AAGUID（二进制16字节）',

  name VARCHAR(100) NOT NULL COMMENT 'Passkey名称/标签（用户自定义）',
  is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为主要Passkey（每用户最多一个建议由业务层保证）',

  last_used_at DATETIME DEFAULT NULL COMMENT '最后使用时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (id),

  CONSTRAINT fk_user_passkeys_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

  UNIQUE KEY uk_user_passkeys_credential (credential_id),

  KEY idx_user_passkeys_user (user_id),
  KEY idx_user_passkeys_user_last_used (user_id, last_used_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='用户Passkey凭证表（challenge/敏感验证迁Redis，MySQL仅保留凭证）';

SET FOREIGN_KEY_CHECKS = 1;
