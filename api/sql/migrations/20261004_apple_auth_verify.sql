-- Read-only verification: run in Auth after applying the migration.
SELECT DATABASE() AS selected_database;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
    (TABLE_NAME = 'user_oauth_accounts'
      AND COLUMN_NAME IN ('provider', 'apple_client_id',
        'apple_refresh_token_encrypted', 'apple_email_forwarding_enabled'))
    OR TABLE_NAME = 'apple_revocation_tasks'
    OR (TABLE_NAME = 'users' AND COLUMN_NAME IN ('email', 'password_hash'))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- EXPECTED: provider VARCHAR(32); three nullable Apple columns;
-- revocation table with id/client_id/encrypted_token/attempts/next_attempt_at;
-- users.email and users.password_hash nullable.
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'apple_revocation_tasks'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
