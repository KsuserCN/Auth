# Console SQL tests

Run against MySQL 8 binaries. This script starts a new MySQL server using a temporary data directory and Unix socket, disables network listening, ignores installed MySQL configuration, and shuts down the process afterward. It never connects to an existing database.

```bash
python3 api/sql/tests/test_console_migrations.py --mysql-bin-dir /path/to/mysql/bin
```

For a MySQL Homebrew bottle extracted into a temporary directory, pass `--library-path` with the extracted and existing runtime library directories. No global install or system service is needed.

The scenarios cover fresh initialization, repeated incremental migrations, missing seed accounts, conflicting client IDs, callback/scope case sensitivity, repeated bootstrap preserving app configuration, legacy admin migration, persistent certifications, grant identity renewal and foreign-key cascade behavior.
