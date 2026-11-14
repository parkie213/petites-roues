# Catalogue migrations

Flyway is the only schema initializer. Spring Boot runs it before application database use;
`spring.sql.init.mode=never` disables the former `schema.sql` path.

`V1__Create_catalogue.sql` creates every table. The catalogue is a rebuildable index (documents
and media are the source of truth), so its history was squashed into V1 on 2026-11-07 rather than
carrying upgrade paths. A catalogue from before then fails at startup, either on V1's changed
checksum or because it has tables but no Flyway history. Delete it (`content/library.sqlite`
by default) and add the folders again.

## Migrate or rebuild

Because the catalogue is a rebuildable index, an incompatible catalogue is rebuilt, not migrated.
While the app runs, use **Reset library** (the "..." menu next to Add folder, API job kind
`reset`). It deletes every table's rows except `repositories`, `settings` and Flyway's history,
then refreshes every registered folder. Reading and playback positions, hand-made media links,
review history and phone pairings are lost, and phones must pair again; folders, files on disk
and settings (including the phone certificate) are kept. It refuses while edits are pending or a
review batch is open, because pending edits exist only in SQLite: process or discard them first.
If the rebuild fails, the folders stay registered and Refresh finishes it.

When the app cannot start (a catalogue that fails Flyway validation), delete the catalogue file
and add the folders again instead.

Add future changes as `V2__Describe_change.sql`, `V3__Describe_change.sql`, etc. Do not edit an
applied migration or add a startup bean that checks columns. Flyway tracks completed versions in
`flyway_schema_history` and validates checksums on startup. Test fixtures migrate through Flyway
too (`DatabaseFixtures`).

## Migrate or rebuild

Prefer a Flyway migration for additive changes: a new table or column. Rebuild instead when a
change would alter or reinterpret stored data, or when carrying it forward would need backfills
and equivalence tests. The catalogue is an index of the documents and media on disk, so a rebuild
is always possible.

A rebuild recreates `repositories`, `titles`, `media`, `accepted_documents` and
`served_documents` from the files. It loses the data that exists only in SQLite: playback and
reading progress, settings, phone pairings, hand-made media links and the review history.
Edits still pending in `review_queue` exist only in SQLite until an agent writes them into the
documents, so never rebuild while any are pending.

To rebuild:

1. Process or discard the review queue until `GET /api/review/pending` returns `[]` and no batch
   is open.
2. Stop the app and copy `content/library.sqlite` somewhere safe.
3. Note anything worth keeping that the list above says is lost, such as phone pairings.
4. Delete `content/library.sqlite`, start the app, add each folder and Refresh it.
5. Pair the phones again.
