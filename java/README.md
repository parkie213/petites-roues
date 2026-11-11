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

## Where React and Android fit

There are two applications with different execution environments and workflows:

| Part | Owns | How it reaches shared functionality |
| --- | --- | --- |
| React UI (`web/`) | Browser presentation, interactions, playback coordination and pending requests | Calls the desktop HTTP API; consumes JSON, not Java jars |
| Desktop Java application | Desktop catalogue, editing/review workflows, persistence and local integrations | Uses the three shared Java modules directly |
| Android app (`android/`) | Offline player/reader, local catalogue and progress, pairing and downloads | Uses the shared Java modules directly; calls the desktop for pairing and transfer |

The React UI reaches `server` through the Node web server's `/api/` proxy.
Controllers delegate to `application` services. The Node server serves the UI
and proxies requests; it does not own the desktop's business workflows. React's
JSON types and display projections are maintained in TypeScript. Sharing a wire
format does not make the Java model executable in the browser.

Android is an offline application, not just another presentation layer over the
desktop service. It has its own Room databases, content-folder permissions and
application services. Compose screens render ViewModel state; ViewModels invoke
local operations or the desktop client. The desktop supplies files through sync,
and playback and reading then work locally. Android is read-only with respect to
study documents, while still saving local progress and preferences.

Consequently, Android does not depend on the Java `application` or `server`
modules. The name `application` currently refers specifically to the desktop.
`desktop-application` would make that clearer when discussing both platforms.
The shared modules are the place for rules the two platforms must agree on,
such as document validation, media fingerprints and discovery. Desktop editing
and Android lifecycle handling remain owned by their respective applications.

### A concrete port and adapter already in use

`library` defines `ContentTree<F>`: list a folder and open a file, using a
platform-specific handle. `ContentScanner` depends on that contract. It does not
need to know how an operating system grants access to the files.

- `PathContentTree` adapts desktop paths to that contract.
- Android's `SafContentTree` adapts Storage Access Framework URIs to the same
  contract, through its `ContentFolder` interface.

This is the central idea of hexagonal architecture in existing code: the shared
rules define what they need; platform implementations supply it. At runtime the
scanner calls the adapter, but the adapter's source code depends on the shared
contract. The path adapter currently lives in the same jar as the contract;
separate Maven modules are not required to express this relationship.

### Applying the idea to the whole system

For the desktop, the HTTP controllers are incoming adapters: they translate an
external action into an application operation. A strict hexagonal design would
also put persistence contracts between services and JDBC stores. The server
would wire those implementations into the application.

For Android, ViewModels connect UI actions to local application operations.
Room, Android storage access, Media3 and the desktop HTTP client are external
integrations around those operations. Some are already separated behind
contracts; others are concrete dependencies. Hilt wires objects together, but
dependency injection alone does not establish a hexagonal boundary.

React can also separate presentation and browser integrations from the operations
they support. Hooks coordinating an operation can call an API client while
components render state. A Java HTTP API is a network contract, however, not the
same thing as a Java interface port. From the desktop's perspective React is an
external client; from the browser's perspective the desktop API is an external
dependency. Each application has its own boundary.

We therefore should not draw a single shared application core with React and
Android as interchangeable skins. They share some rules and data contracts,
but they perform different operations and own different state. A useful next
step would be to identify a specific workflow whose tests or integration changes
are difficult, then introduce a port at that boundary if it helps. A system-wide
rewrite or a common UI abstraction is not required.

See [the React code map](../web/README.md) and
[the Android code map](../android/README.md) for platform-specific ownership.

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
