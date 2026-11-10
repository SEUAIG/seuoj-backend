# Production database migrations

Fresh databases use `sql/database_schema.sql`; do not run these ALTER scripts after fresh initialization.
Existing production databases retain their data. Back up first and apply only migrations not already present.
No migration is run automatically by the application or by these source-control changes.

Apply in this order (once per existing database):

1. `20261103_contest_members.sql`
2. `20261103_contest_roster.sql`
3. `20261104_account_permissions.sql` (requires `user_info.temporary_contest_id` from roster)
4. `20261105_contest_notices.sql`
5. `20261105_user_ban.sql`
6. `20261106_submission_skip.sql`
7. `20261106_system_setting.sql`

The permissions migration was previously an untracked file named `20261103_account_permissions.sql`.
If that script was already applied manually, it is the same migration: do not run the renamed file again.
Check columns and tables in `information_schema` before applying an old migration.
Never run the full initialization schema against an existing production database: it contains DROP TABLE statements.

Production data, database dumps, certificates and environment secrets belong outside the Git repository.
