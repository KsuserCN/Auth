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


DROP TABLE IF EXISTS users;
CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键ID',
  uuid CHAR(36)
    CHARACTER SET ascii
    COLLATE ascii_bin
    NOT NULL
    COMMENT '公开用户ID(sub)，OAuth返回给第三方使用（严格大小写匹配）',
    
  username VARCHAR(50) NOT NULL COMMENT '登录用户名',
  email VARCHAR(255) DEFAULT NULL COMMENT '邮箱（可选）',
  password_hash VARCHAR(255) DEFAULT NULL COMMENT '密码哈希（Passkey用户可为空）',

  real_name VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  gender ENUM('male','female','secret') DEFAULT 'secret' COMMENT '性别',
  birth_date DATE DEFAULT NULL COMMENT '出生日期 YYYY-MM-DD',
  region VARCHAR(100) DEFAULT NULL COMMENT '地区',
  bio VARCHAR(200) DEFAULT NULL COMMENT '个人简介（最多200字）',

  avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
             ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (id),
  UNIQUE KEY uk_users_uuid (uuid) COMMENT '唯一约束：对外公开UUID(sub)不可重复',
  UNIQUE KEY uk_users_username (username) COMMENT '唯一约束：用户名不可重复',
  UNIQUE KEY uk_users_email (email) COMMENT '唯一约束：邮箱不可重复（NULL可重复）'

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';


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

-- TOTP 相关表清理
DROP TABLE IF EXISTS totp_recovery_codes;
DROP TABLE IF EXISTS user_totp;

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

-- ==========================================================
-- TOTP（Time-based One-Time Password）方案：用户一次性密码认证
-- 说明：
--   - user_totp：存储用户 TOTP 配置（加密密钥、启用状态等）
--   - totp_recovery_codes：存储回复码（以防用户丢失 TOTP 设备）
-- 安全考虑：
--   - secret_key_ciphertext：AES-GCM 加密的 secret（不可逆），验证时解密后使用
--   - key_version：密钥版本，便于轮换
--   - pending_secret_ciphertext：待确认的秘密值（注册流程中）
--   - pending_expires_at：待确认秘密的过期时间（10 分钟）
--   - confirmed_at：TOTP 最终确认时间（确认后清空 pending_*）
--   - last_used_step：上次成功验证的时间步长，防重放
-- ==========================================================

DROP TABLE IF EXISTS user_totp;
CREATE TABLE user_totp (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'TOTP配置主键ID',

  user_id BIGINT UNSIGNED NOT NULL COMMENT '关联用户ID（users.id）',

  secret_key_ciphertext VARBINARY(512) DEFAULT NULL COMMENT 'TOTP 密钥（AES-GCM 加密），解密后是 Base32 编码的密钥',

  key_version INT NOT NULL DEFAULT 1 COMMENT '密钥版本，便于轮换',

  is_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 TOTP（0=禁用 1=启用）',

  pending_secret_ciphertext VARBINARY(512) DEFAULT NULL COMMENT '待确认的秘密值（注册流程中，确认后删除）',

  pending_expires_at DATETIME DEFAULT NULL COMMENT '待确认秘密的过期时间（建议 10 分钟过期）',

  confirmed_at DATETIME DEFAULT NULL COMMENT '最终确认时间（TOTP 首次启用的时间）',

  last_used_step BIGINT DEFAULT NULL COMMENT '上次成功验证的时间步长（floor(Unix_timestamp/30)），防重放',

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (id),

  CONSTRAINT fk_user_totp_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

  UNIQUE KEY uk_user_totp_user (user_id),
  
  KEY idx_user_totp_enabled (user_id, is_enabled)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='用户TOTP配置表（密钥加密存储，支持密钥轮换，防重放）';


DROP TABLE IF EXISTS totp_recovery_codes;
CREATE TABLE totp_recovery_codes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '回复码主键ID',

  user_id BIGINT UNSIGNED NOT NULL COMMENT '关联用户ID（users.id）',

  code_hash VARBINARY(32) NOT NULL COMMENT '回复码哈希（SHA-256），不存储明文',

  code_ciphertext VARBINARY(256) DEFAULT NULL COMMENT '回复码密文（AES-GCM，加密后的原始恢复码）',

  used_at DATETIME DEFAULT NULL COMMENT '使用时间（NULL 表示未使用）',

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (id),

  CONSTRAINT fk_totp_recovery_codes_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

  UNIQUE KEY uk_recovery_codes_user_hash (user_id, code_hash) COMMENT '防止重复插入同一个码的哈希',
  
  KEY idx_recovery_codes_user_unused (user_id, used_at) COMMENT '快速查询用户的未使用回复码'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='TOTP回复码表（一次性使用，哈希存储，used_at为NULL表示未使用）';

SET FOREIGN_KEY_CHECKS = 1;
