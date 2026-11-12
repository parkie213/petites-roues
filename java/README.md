# Java modules

The build shares portable document and library rules between desktop and Android.
React calls the desktop over HTTP; Android runs its own offline application.

| Module | Owns | Internal production dependencies |
| --- | --- | --- |
| `content-model` | Study documents, Markdown quizzes/notes, defaults and intrinsic rules | None |
| `study-prompts` | Prepare source material and instructions for LLM study prompts | `content-model` |
| `document-json` | JSON recognition, strict decoding, canonical encoding and file loading | `content-model` |
| `content-library` | Repository identities, library entries, discovery, shelving, media linking and fingerprints | `content-model` |
| `sync` | Desktop–phone protocol, client and downloads | `content-library` |
| `desktop-application` | Desktop workflows, transactions, SQLite stores/migrations and local integrations | Model, JSON, library and sync |
| `document-test-fixtures` | Reusable sample documents and model constructors for downstream tests | `content-model` |
| `desktop-test-fixtures` | Test database setup and synthetic media | Flyway (external) |
| `desktop-server` | HTTP controllers/filters, Spring Boot startup, phone listener and advertising | `desktop-application`, `document-json` |

Module artifact IDs match directory names under `app.petitesroues`. The parent is
`app.petitesroues:petites-roues-parent`. The parent manages internal dependency
versions, including ordinary fixture JARs, in `dependencyManagement`; child modules
declare only the dependencies they need. Fixture dependencies also inherit test
scope from the parent. Each child still declares its parent
version because Maven must resolve the parent before inheriting that management.
The five shared modules compile production
code for Java 17 for Android; the desktop modules and all tests use Java 25.

## Content models and document JSON

`content-model` is Java-only. It owns annotated video/comic documents and Markdown
quiz/note pages. `MarkdownPage` shares page data and the first-heading title rule.
It contains title and text, without repository or storage fields.
`content-library` owns shared library models under `app.petitesroues.model.library`:
`repository.Repository` (identity and name), `pages.MarkdownPageEntry` (repository,
relative path and content), `documents.DocumentEntry` (repository, relative path
and indexed metadata), and `media.MediaEntry` (repository ID, relative path, kind
and fingerprint). `DocumentMetadata` extracts the same video/comic summary on both
platforms, without retaining cues or regions. Media linking consumes these shared
entries; videos and comics use the same entry types, distinguished by media kind.
Platform readers load content; library services and scanners associate it with a repository. Rendering and quiz
sharing remain in the user interfaces. Local learner progress remains separate
from portable content and currently lives in each application.

Its records and `StudyDocumentValidator` have no
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

Pure model tests stay in `content-model` and construct their own inputs. Reusable
model fixtures live in `document-test-fixtures` for downstream consumers. The model
cannot depend back on its fixture module, even for tests, without a reactor cycle.
JSON shape, recognition, canonical output and round-trip tests belong to `document-json`.

## Content library

`content-library` owns portable discovery, linking, shelving, fingerprints and
progress rules. `ContentTree` isolates filesystem access for desktop paths and
Android storage. These rules have no database or framework dependency.

`NaturalOrder` owns title and CBZ-page ordering independently of shelf grouping.
It compares decimal digit runs numerically (including Unicode decimal digits),
places numeric runs before text runs, ignores case, distinguishes accents and uses textual order to break numeric ties
such as leading zeros. Desktop and Android CBZ readers use this same comparator.

ICU4J 79.1 numeric collation was evaluated before adding a dependency. It changes
punctuation ordering, treats leading-zero variants as equal and splits numeric
runs beyond 254 digits; the standalone JAR is about 15 MB. We retain the small
JDK-based comparator and its ordering contract. Commons IO is also unnecessary
for the single extension lookup: filenames without a dot have no extension.
The discovery, media-link precedence and fingerprint protocol remain project rules.

## Study prompts

`ComicDocument.chapter(index)` owns chapter page selection: it excludes preface
pages, stops at the next chapter and preserves manifest reading order. Invalid
indices or boundary pages fail explicitly. This is document behavior, independent
of quizzes.

`study-prompts` prepares material and instructions for an LLM; it does not run
quizzes, assess answers or implement study activities. It currently owns
`ChapterQuizBuilder`: deterministic Markdown construction
from a selected chapter, teaching rules and optional type instructions. It includes
source text and translations, omits learner notes and suggests a filename. It has
no framework, filesystem, sharing or AI-client dependency. Android calls it offline;
its video and general-quiz builders remain platform code.

Desktop `ChapterQuizService` loads the current served document, including queued
edits. `POST /api/titles/{id}/comic/quiz` accepts `chapterPageId`, `rules` and optional
`type` (`label`, `instructions`), and returns `text` and `fileName` with no-store
caching. Chapter identity is its first page ID, so a stale menu cannot silently
select a different chapter after reordering. This operation writes no content or
progress. Callers supply their bundled teaching guide and selected menu instructions.

React prepares on dialog open/selection change, cancels stale requests and enables
export only for the current result. Clipboard, chat opening, downloading and system
sharing stay in the UI and run within the export click's user gesture.

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

HTTP integration tests stay in `desktop-server`. `document-test-fixtures` and
`desktop-test-fixtures` are ordinary JAR modules, consumed with test scope. There
are no attached test JARs, classifiers or sibling source-directory links. Neither
fixture JAR is packaged into the production server.

`desktop-test-fixtures` deliberately has no desktop-application dependency: its
Flyway helper uses the consuming test's migration classpath, and media samples
are generic. This lets application and server tests both use it without a cycle.
Application-specific helpers remain local to application tests. Actual test
classes remain in the modules whose behaviour they verify.

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
./java/mvnw -B -f java/pom.xml -pl content-model -am verify
./java/mvnw -B -f java/pom.xml -pl desktop-server -am verify
```

Media integration tests require FFmpeg. CI builds shared artifacts before desktop
and Android consumers; see [the workflow](../.github/workflows/ci.yml).
