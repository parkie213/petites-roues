# Java modules

The build shares portable document and library rules between desktop and Android.
React calls the desktop over HTTP; Android runs its own offline application.

| Module | Owns | Internal production dependencies |
| --- | --- | --- |
| `document-model` | Immutable document types, defaults, intrinsic validation and playback ordering | None |
| `document-json` | JSON recognition, strict decoding, canonical encoding and file loading | `document-model` |
| `content-library` | Discovery, shelving, media linking, fingerprints and progress calculations | None |
| `sync` | Desktop–phone protocol, client and downloads | `content-library` |
| `desktop-application` | Desktop workflows, transactions, SQLite stores/migrations and local integrations | Model, JSON, library and sync |
| `desktop-server` | HTTP controllers/filters, Spring Boot startup, phone listener and advertising | `desktop-application`, `document-json` |

Module artifact IDs match directory names under `app.petitesroues`. The parent is
`app.petitesroues:petites-roues-parent`. The four shared modules compile production
code for Java 17 for Android; the desktop modules and all tests use Java 25.

## Documents and JSON

`document-model` is Java-only. Its records and `StudyDocumentValidator` have no
Jackson annotations, mappers, serialization methods or DI-framework dependencies.
Models own defaults, immutable collections and intrinsic rules, including exactly
one video or comic body. `VideoDocument.inPlaybackOrder()` is an explicit domain
operation; decoding alone does not reorder cues.

`document-json` is an adapter around that model. Jackson configuration, mix-ins,
the root wire representation and enum spelling live here. The Java document has
one `StudyBody`; JSON has exactly one `video` or `comic` property. Optional-field
omission and canonical formatting are serialization policies, not domain rules.
The adapter maps these differences without duplicating every model record.

`StudyDocumentCodec` owns its JSON implementation details. It recognises candidate
JSON, strictly decodes and validates it, and validates before canonical encoding.
Recognition deliberately tolerates broken documents so Refresh reports them
rather than skipping them. Inspection returns a valid document or its problems,
without a second parse. The codec preserves array order.

`StudyDocumentReader` delegates all JSON work to the codec. It loads files or bytes
for study and explicitly applies the model's chronological video ordering; comic
order is preserved. `DocumentStore` uses only this reader to load snapshots and
inspect candidates. Phone transfer uses the codec to encode documents.

Spring and Hilt construct a codec and reader. They do not configure document
mappers or pretty printers. HTTP JSON and persisted review commands register the
adapter's Jackson module so nested domain objects use the same wire representation.
The HTTP mapper retains its own request-handling policies.

Pure model tests and reusable model fixtures live in `document-model`. JSON shape,
recognition, canonical output and round-trip tests belong to `document-json`.

## Desktop application and server

`desktop-application` owns use cases and their supporting implementations. It uses
Spring services, JDBC and transactions, but has no HTTP or servlet dependency.
Services coordinate workflows and transactions; stores contain SQL and row mapping;
scanners discover content; readers decode file formats. Models are grouped by
responsibility under `app.petitesroues.model`.

SQLite migrations live with the stores in `desktop-application`. Non-HTTP database
tests run here without starting the server. Flyway is a test dependency of this
module; services do not initialise the database themselves. The server owns Boot's
Flyway startup integration and discovers migrations from the application JAR.
Migration SQL and checksums must remain unchanged when moving resources.

HTTP integration tests stay in `desktop-server`. Test-only fixture JARs provide
model fixtures and desktop helpers/media without sibling source-directory links.
These fixtures are excluded from production artifacts.

## React, Android and ports/adapters

React owns browser presentation and interactions. Its Node server serves assets
and proxies `/api/` requests to the desktop HTTP server. It consumes JSON, not Java
classes. Android owns Compose/ViewModel interactions, Room databases, permissions
and local playback. It uses the shared Java modules directly and contacts the
desktop for pairing and transfer. It does not depend on the desktop modules.

Hexagonal architecture separates application rules from external technologies
through contracts (ports) and their implementations (adapters). The hexagon does
not prescribe six layers or Maven modules. `ContentTree` is an existing example:
shared discovery uses its contract, while desktop paths and Android storage
supply implementations. JSON is another adapter around the document model.

This project does not implement strict hexagonal architecture throughout: desktop
services still use concrete JDBC stores. Introduce further ports where they make
integration changes or testing easier. React and Android share some rules and
contracts but have different workflows and state; they are not interchangeable
skins around a single application core.

See [the React code map](../web/README.md), [the Android code map](../android/README.md)
and [workspace conventions](../AGENTS.md).

## Verification

From the repository root:

```sh
./java/mvnw -B -f java/pom.xml verify
./java/mvnw -B -f java/pom.xml -pl document-model -am verify
./java/mvnw -B -f java/pom.xml -pl desktop-server -am verify
```

Media integration tests require FFmpeg. CI builds shared artifacts before desktop
and Android consumers; see [the workflow](../.github/workflows/ci.yml).
