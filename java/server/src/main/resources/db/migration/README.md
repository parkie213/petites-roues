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
