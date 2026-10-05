-- Incremental migration; preserves existing accounts and sessions.
CREATE TABLE IF NOT EXISTS user_push_devices (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  device_token VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  environment VARCHAR(16) NOT NULL,
  mode VARCHAR(32) NOT NULL DEFAULT 'ALL',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_push_device_token (device_token, environment),
  UNIQUE KEY uk_push_device_session (session_id),
  CONSTRAINT fk_push_device_session FOREIGN KEY (session_id)
    REFERENCES user_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
