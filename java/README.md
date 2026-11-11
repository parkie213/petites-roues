# Java modules

The Java build provides shared code for desktop and Android, plus the desktop
application and its HTTP server. This describes the current boundaries and the
naming questions still open; it does not prescribe a module reorganisation.

## Current modules

| Module | Owns | Production dependencies within this build |
| --- | --- | --- |
| `documents` | Study-document records, intrinsic validation, JSON encoding/decoding and file reading | None |
| `library` | Portable content discovery, shelving, media linking, progress calculations and fingerprints | None |
| `sync` | Desktop–phone protocol records, pairing-code reading, desktop client and download/transfer logic | `library` |
| `application` | Desktop use cases, transaction boundaries, SQLite stores, document-edit replay, filesystem and media integrations | `documents`, `library`, `sync` |
| `server` | HTTP controllers and filters, Spring Boot startup/configuration, database migrations and phone listener/advertising | `application` |

The table lists direct production dependencies between our modules. Jackson,
Spring and other external dependencies are declared in each module's POM.

`documents`, `library` and `sync` compile production code for Java 17 so Android
can consume them. Their tests use the build's Java 25 toolchain. `application`
and `server` target Java 25. The shared modules have no Spring dependency;
`application` uses Spring context, JDBC and transactions, and `server` supplies
the executable Spring Boot host.

Dependencies point from the server towards application operations and shared
code. Shared code must not depend on the desktop application or HTTP server.
The parent `pom.xml` aggregates the modules and manages build configuration;
it has no application implementation.

## Naming choices

### `documents` or `document-model`?

`document-model` would clearly identify the shared study-document types, but the
current module also contains `StudyDocumentCodec`, `StudyDocumentReader` and
`StudyDocumentValidator`, and depends on Jackson. It owns the portable document
format as well as its in-memory model.

For the current contents, `documents` is a reasonable name. `study-documents`
would be more specific. Reserve `document-model` for a deliberate boundary in
which the module owns domain types and intrinsic rules, while JSON and file I/O
live elsewhere. Splitting these just to obtain that name would add another
module without an established consumer need.

### `library`

This means the user's content library, not a generic collection of reusable
Java utilities. It contains rules shared by desktop and Android. Desktop library
services and SQLite stores live in `application`, even though their Java
packages also begin with `com.petitesroues.library`. `content-library` is a
possible clearer module name if this ambiguity becomes troublesome.

### `sync`

This module includes both the shared protocol and the phone-side desktop client
and transfer implementation. `sync` describes that combined scope. Calling it
`sync-protocol` would understate its contents. Separate protocol and client
modules only if their consumers or dependencies need independent boundaries.

## Why separate `application` and `server`?

The application owns operations such as adding a repository, queuing an edit or
saving playback progress. The server exposes those operations over HTTP:
decoding requests, delegating to services, and producing responses and errors.
It also assembles and starts the desktop runtime.

This separation prevents application code from depending on Spring MVC or
servlet APIs and allows workflows to be tested without an HTTP server. It also
leaves room for another entry point, such as a command-line tool, to call the
same services. Such an entry point is a possibility, not an existing requirement.

`application` is not framework-independent: it includes concrete JDBC stores,
Spring services and local integrations. Its name means desktop application
operations and supporting implementation, not a pure domain core. `server`
means the executable HTTP host; `desktop-server` could make that more explicit.

Keeping both modules makes sense while the HTTP dependency boundary is useful.
Merging them would simplify Maven configuration but lose that compile-time
separation. Adding further modules should solve a concrete dependency or reuse
problem rather than make the diagram more symmetrical.

## Hexagonal architecture

Hexagonal architecture is also called ports and adapters. It places application
rules and use cases at the centre, with external systems connected through
explicit contracts (ports). Adapters translate between those contracts and
technologies such as HTTP, SQLite, files or an external process. The hexagon is
a drawing convention; it does not mean six layers or six modules.

For example, a playback-progress use case could depend on a `PlaybackProgress`
persistence interface owned by the core. A JDBC adapter would implement that
interface. An HTTP controller would call the use case. The core would know the
persistence contract but would not import JDBC or HTTP types. Both the HTTP
adapter and the JDBC adapter would depend towards the core, with runtime wiring
connecting the implementations.

The current project has some of this separation: HTTP controllers are outside
application operations, and portable document/library rules are outside Spring.
It is not a strict hexagonal implementation. Services use concrete stores, and
JDBC implementations share the `application` module with the use cases.

Adopting stricter ports and adapters would mean defining interfaces at selected
external boundaries and moving their implementations outward. It would not
require an interface for every class or a Maven module for every adapter. Do it
where substituting an integration or testing an operation becomes materially
easier; the existing service/store/scanner/reader conventions remain useful.

## Responsibility conventions

- Services coordinate operations and own transaction boundaries; they contain no
  SQL, JDBC calls or database row mapping.
- Stores own database queries, persistence mapping and writes.
- Scanners discover content without accessing the database.
- Readers decode particular file formats.
- Controllers handle HTTP requests and responses and delegate operations.
- Shared model types live under `com.petitesroues.model`, grouped by purpose.

Module names describe larger ownership boundaries; class names describe the
responsibilities within them. See [the workspace instructions](../AGENTS.md).

## Build and verification

From the repository root, run the complete Java build with:

```sh
./java/mvnw -B -f java/pom.xml verify
```

To build the server and its required modules:

```sh
./java/mvnw -B -f java/pom.xml -pl server -am verify
```

Media integration tests require FFmpeg. CI installs and tests the shared modules
first, then verifies `application` and `server` against those artifacts. See
[the CI workflow](../.github/workflows/ci.yml) for the exact commands.
