# Catalogue migrations

Flyway is the only schema initializer. Spring Boot runs it before application database use;
`spring.sql.init.mode=never` disables the former `schema.sql` path.

`V1__Create_catalogue.sql` creates every table. The catalogue is a rebuildable index (documents
and media are the source of truth), so its history was squashed into V1 on 2026-11-07 rather than
carrying upgrade paths. A catalogue from before then fails at startup, either on V1's changed
checksum or because it has tables but no Flyway history. Delete it (`content/library.sqlite`
by default) and add the folders again.

Add future changes as `V2__Describe_change.sql`, `V3__Describe_change.sql`, etc. Do not edit an
applied migration or add a startup bean that checks columns. Flyway tracks completed versions in
`flyway_schema_history` and validates checksums on startup. Test fixtures migrate through Flyway
too (`DatabaseFixtures`).

## Migrate or rebuild

Prefer a Flyway migration for additive changes: a new table or column. Rebuild instead when a
change would alter or reinterpret stored data, or when carrying it forward would need backfills
and equivalence tests. The catalogue is an index of the documents and media on disk, so a rebuild
is always possible.

While the app runs, use **Reset library** (the "..." menu next to Add folder, API job kind
`reset`). It deletes every table's rows except `repositories`, `settings` and Flyway's history,
then refreshes every registered folder. Reading and playback positions, hand-made media links,
review history and phone pairings are lost, and phones must pair again; folders, files on disk
and settings (including the phone certificate) are kept. It refuses while edits are pending or a
review batch is open, because edits pending in `review_queue` exist only in SQLite until an agent
writes them into the documents: process or discard them first (`GET /api/review/pending` returns
`[]`). If the rebuild fails, the folders stay registered and Refresh finishes it.

When the app cannot start (a catalogue that fails Flyway validation), rebuild by hand:

1. Copy `content/library.sqlite` somewhere safe.
2. Delete it, start the app, add each folder and Refresh it.
3. Pair the phones again.
