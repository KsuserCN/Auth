#!/bin/zsh
set -euo pipefail
cd "$(dirname "$0")/../../.."
TEST_ROOT=$(mktemp -d /private/tmp/ksuser-sql-test.XXXXXX)
MYSQL_SOCKET="$TEST_ROOT/mysql.sock"
MYSQL_PID="$TEST_ROOT/mysql.pid"
cleanup() {
  if [[ -f "$MYSQL_PID" ]]; then kill "$(cat "$MYSQL_PID")" 2>/dev/null || true; fi
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT
mkdir "$TEST_ROOT/data"
mysqld --no-defaults --initialize-insecure --datadir="$TEST_ROOT/data" --log-error="$TEST_ROOT/init.log"
mysqld --no-defaults --skip-networking --datadir="$TEST_ROOT/data" --socket="$MYSQL_SOCKET" --pid-file="$MYSQL_PID" --log-error="$TEST_ROOT/server.log" --user="$(id -un)" &
for i in {1..30}; do
  if mysqladmin --no-defaults --socket="$MYSQL_SOCKET" -uroot ping >/dev/null 2>&1; then break; fi
  sleep 1
done
mysqladmin --no-defaults --socket="$MYSQL_SOCKET" -uroot ping >/dev/null
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot -e 'CREATE DATABASE ksuser_migration_test; CREATE TABLE ksuser_migration_test.official_applications (id BIGINT PRIMARY KEY, reserved TINYINT(1) NOT NULL DEFAULT 0); CREATE TABLE ksuser_migration_test.oidc_clients (client_id VARCHAR(128) PRIMARY KEY); CREATE TABLE ksuser_migration_test.user_sso_authorizations (id BIGINT PRIMARY KEY); INSERT INTO ksuser_migration_test.oidc_clients VALUES ("ezbookkeep_old");'
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test < api/sql/migrations/20261014_official_optional_nonce.sql
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test < api/sql/migrations/20261014_official_optional_nonce.sql
if mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test < api/sql/migrations/20261014_retire_sso.sql > "$TEST_ROOT/retire.out" 2>&1; then
  echo 'FAIL: retirement should reject remaining clients'; exit 1
fi
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test -e 'DELETE FROM oidc_clients;'
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test < api/sql/migrations/20261014_retire_sso.sql
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_migration_test < api/sql/migrations/20261014_retire_sso.sql
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot -e 'CREATE DATABASE ksuser_fresh_test;'
mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot ksuser_fresh_test < api/sql/init.sql
RESULT=$(mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ksuser_migration_test' AND table_name='official_applications' AND column_name='allow_missing_nonce'; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ksuser_migration_test' AND table_name IN ('oidc_clients','user_sso_authorizations');")
if [[ "$RESULT" != $'1\n0' ]]; then echo "FAIL: unexpected schema $RESULT"; exit 1; fi
FRESH=$(mysql --no-defaults --socket="$MYSQL_SOCKET" -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ksuser_fresh_test' AND table_name='official_applications' AND column_name='allow_missing_nonce'; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ksuser_fresh_test' AND table_name IN ('oidc_clients','user_sso_authorizations');")
if [[ "$FRESH" != $'1\n0' ]]; then echo "FAIL: unexpected fresh schema $FRESH"; exit 1; fi
echo 'PASS: fresh schema, optional nonce migration, retirement guard and cleanup'
