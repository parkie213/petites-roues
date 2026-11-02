# Catalogue migrations

Flyway is the only schema initializer. Spring Boot runs it before application database use;
`spring.sql.init.mode=never` disables the former `schema.sql` path.

- `V1__Create_catalogue.sql` creates all application tables on a fresh database. Its
  `IF NOT EXISTS` statements also create tables missing from an older catalogue.
- `V2__Adopt_unversioned_catalogues` is the one-time Java handoff for pre-Flyway
  databases. It fills portable identities, adds media/decision fields, and converts
  old queue snapshots to cue IDs and suggestion text. Flyway owns its transaction.

Existing unversioned catalogues are baselined at **0**, so neither migration is
skipped. Already-current rows, registrations, progress, review decisions and queue
sequence high-water marks are preserved. Only the SQLite catalogue is migrated;
study JSON and media are not opened or changed. Migration failure stops startup;
malformed legacy rows can be corrected and startup retried after rollback.

Add future changes as `V3__Describe_change.sql`, `V4__Describe_change.sql`, etc.
Do not edit an applied migration or add a startup bean that checks columns.
Flyway tracks completed versions in `flyway_schema_history` and validates SQL
checksums on startup. Fresh and upgrade fixtures must use Flyway too; direct V1
execution is reserved for constructing deliberately unversioned migration fixtures.
