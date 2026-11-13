# Java modules

The build shares portable document and library rules between desktop and Android.
React calls the desktop over HTTP; Android runs its own offline application.

| Module | Owns | Internal production dependencies |
| --- | --- | --- |
| `content-model` | Study documents, Markdown quizzes/notes, defaults and intrinsic rules | None |
| `study-prompts` | Prepare source material and instructions for LLM study prompts | `content-model` |
| `learner-state-model` | Video/page resume positions, validation and progress fractions | None |
| `document-json` | JSON recognition, strict decoding, canonical encoding and file loading | `content-model` |
| `content-library` | Repository identities, library entries, discovery, shelving, media linking and fingerprints | `content-model` |
| `sync` | Desktop–phone protocol, client and downloads | `content-library` |
| `desktop-application` | Desktop workflows, transactions, SQLite stores/migrations and local integrations | Content and learner-state models, study prompts, JSON, library and sync |
| `document-test-fixtures` | Reusable sample documents and model constructors for downstream tests | `content-model` |
| `desktop-test-fixtures` | Test database setup and synthetic media | Flyway (external) |
| `desktop-server` | HTTP controllers/filters, Spring Boot startup, phone listener and advertising | `desktop-application`, `document-json` |

Module artifact IDs match directory names under `app.petitesroues`. The parent is
`app.petitesroues:petites-roues-parent`. The parent manages internal dependency
versions, including ordinary fixture JARs, in `dependencyManagement`; child modules
declare only the dependencies they need. Fixture dependencies also inherit test
scope from the parent. Each child still declares its parent
version because Maven must resolve the parent before inheriting that management.
The six shared modules compile production
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
sharing remain in the user interfaces. Learner position rules live in `learner-state-model`; each application persists its
own progress separately from portable content.

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

`ComicDocument.pagesForChapter(index)` owns chapter page selection: it excludes preface
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

## Learner progress

`learner-state-model` owns `PlaybackPosition` and `ReadingPosition` under
`app.petitesroues.model.progress`. It has no production dependencies and is shared
by desktop and Android. Positions have no storage key, timestamp or database fields.
Video positions must be finite and nonnegative; fractions are clamped to one, or
unknown for nonfinite/nonpositive/absent durations. Comic fractions count the selected
page in the current ordered manifest. Missing saved page IDs restore to the first
page; new saves must name a page in the current manifest.

Desktop progress snapshots and HTTP fields retain ISO timestamp strings and local
title IDs. Android's Room entity retains epoch-millisecond timestamps and its existing
progress keys. Both map position values at application boundaries. Storage schemas,
write ordering and clear operations stay local. `LibraryProgress` in `content-library`
continues to report Add/Refresh work; it is not learner progress. The desktop continues to hide a
zero-position progress bar; the shared numeric fraction for zero is zero.

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

### Transactions and concurrency

Correctness comes from short SQLite transactions, not from a Java lock. This depends on the
database having one pooled connection (`spring.datasource.hikari.maximum-pool-size=1` in the
server's `application.properties`; the pool also sets `busy_timeout`): a transaction holds the
connection, so all other database work waits for it. With a larger pool the read-then-write
checks below would no longer be atomic. A test fails if the pool size changes.

- Every player or reader edit loads the served document, validates the edit and queues it in one
  transaction (`DocumentEditingService`), as does a review request. Claim, complete, discard and
  import decide and write in one transaction; their file reads happen before it.
- Add and Refresh discover (scan, read, probe) with no lock and no transaction, so edits proceed
  meanwhile. The commit is one transaction that first checks the repository still exists and has
  not been refreshed since discovery began; otherwise it changes nothing and the job fails with a
  message. Removal checks for unwritten edits and deletes in one transaction.
- No file, process or network access happens inside these transactions.

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

### ArchUnit trial

`desktop-server` has a test-scoped ArchUnit dependency and four ordinary JUnit
architecture checks. They enforce existing responsibilities from `AGENTS.md`:

- Services, including nested classes, must not depend on JDBC types.
- Scanners, including nested classes, must not depend on JDBC, named stores or
  the `ReviewQueue` persistence component.
- Desktop application code must not depend on the HTTP adapter, server
  configuration, Spring HTTP/web types or servlet types.
- `content-model` and `learner-state-model` must stay independent of Spring,
  JDBC, JPA, servlets and Jackson.

The checks run in the normal server test suite, with no Spring context, database
or production annotations. To run just these checks from the repository root:

```sh
./java/mvnw -B -f java/pom.xml -pl desktop-server -am test \
  -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

Imports follow the production artifact locations of representative classes,
rather than treating all `app.petitesroues.model` packages as one module. This
checks class directories locally and shared JARs in CI, and excludes fixture/test
artifacts. Desktop command models can therefore retain their existing Jackson
annotations without weakening the portable model rule. ArchUnit's default
failure on empty rule selections remains enabled.

These are static dependency checks, not proof of transaction correctness,
absence of SQL string literals or indirect database access through arbitrary
helpers. They do not require cycle-free feature packages or a particular logical
module API. There are no violation baselines or ignored dependencies. The
trial is independent of the Modulith and MCP experiments.

Reference: [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html).

### Java build

From the repository root:

```sh
./java/mvnw -B -f java/pom.xml verify
./java/mvnw -B -f java/pom.xml -pl content-model -am verify
./java/mvnw -B -f java/pom.xml -pl desktop-server -am verify
```

Media integration tests require FFmpeg. CI builds shared artifacts before desktop
and Android consumers; see [the workflow](../.github/workflows/ci.yml).

### Logging

Application and server code use SLF4J (`LoggerFactory.getLogger(Owner.class)`).
`desktop-application` declares `slf4j-api` directly; the executable server supplies
Logback through Spring Boot's default logging starter. Keep backend configuration
in the executable module. Use parameterized `{}` messages and pass exceptions as
the final argument when a stack trace is needed; avoid logging document bodies,
learner notes or credentials. Boot's default output is the console; configure
`logging.file.name` when a retained log file is required.
