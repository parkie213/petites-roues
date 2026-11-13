# Plan: explicit state ownership and publication

Status: proposal, 13 November 2026. This describes the target and migration work;
the accepted-state migration is not implemented yet. The endpoint consolidations
are implemented separately; [HTTP-API.md](HTTP-API.md) records their current contracts.

## Purpose

Make correctness follow from a few operation boundaries instead of coordination
between controllers, service monitors, transactions and live document files.

Today a served document combines a file read with several queue queries. A review
completion can occur between those reads. Repository discovery also holds a shared
monitor while doing slow filesystem and subprocess work. Phone routing depends on
a connector reference that changes during startup and shutdown. Each is a case
where one operation observes several independently changing authorities.

The target is a SQLite-backed application view, changed through short application
transactions. Agents retain ownership of portable files. Importing a file into the
application becomes an explicit publication operation. Network listeners have
separate route sets whose authority does not change with lifecycle state.

This plan covers the desktop application, HTTP server and their browser/agent
contracts. Android retains its offline storage model. Shared document formats and
phone protocol changes must remain compatible with their consumers.

The [endpoint and operation inventory](ENDPOINT-OPERATIONS.md) maps all current
routes to callers, state ownership and transaction boundaries. Use its cutover
checklist when implementing this plan. SQLite remains the chosen database;
accepted documents are stored as JSON alongside transactional local state.
The [endpoint consolidation proposal](ENDPOINT-CONSOLIDATION.md) groups the full
surface by capability and proposes a shared typed-command endpoint, unified
progress and a coherent study-state read. Agree those contracts before changing
the schema or migrating callers; optional resource URL cleanup can follow later.

## Invariants

1. A document response comes from one accepted base revision and one consistent
   snapshot of its pending edits and review state. It never reads a live file to
   finish reconstructing that response.
2. A successful edit means its command and any generated identities are committed.
   A successful publication means its accepted document, metadata, review decisions
   and resulting pending state are committed together.
3. An operation validates the state it actually changes. A stale expected version
   produces a conflict, not an overwrite or implicit retry with different meaning.
4. Filesystem traversal, probing, downloads, native synthesis and Git operations do
   not run inside application database transactions.
5. Slow work cannot recreate a removed repository or replace a newer accepted
   document with an older discovery result.
6. An edit queued after a review claim survives that review's completion. Commands
   that conflict with a published revision remain visible for review.
7. The application never writes portable document files. Agents publish files with
   atomic replacement and checkpoint them in their existing content upstream.
8. The phone listener cannot dispatch local administration endpoints, including
   during startup, shutdown and requests already in flight.
9. Local paths, revisions, progress and queue history remain outside portable JSON.
10. Existing pending edits, batches, decisions and local title identities survive
    migration. The database is durable user state, not a disposable catalogue.

## State and responsibility

| State or resource | Authority and owner |
| --- | --- |
| Portable study content | Agent-owned files and their content repository |
| Accepted application document | Validated document bytes in SQLite, with a revision and source hash |
| Pending changes and review decisions | SQLite queue and batch state |
| Served document | Accepted document plus pending commands, evaluated from one database snapshot |
| Catalogue summaries | Derived from the accepted document in its publication transaction |
| Media availability and matching | SQLite index updated by explicit Add/Refresh; media bytes remain external |
| Learner progress and settings | SQLite, with their own application operations |
| Discovery proposal | Temporary result of a scanner/reader/probe operation, carrying its starting versions |
| Job status and progress | Executor-owned operational state; never evidence that domain state committed |
| Phone route authority | Dedicated server context containing only phone endpoints |

Use existing modules. Services own application logic and transactions; stores own
SQL and mapping; scanners discover; readers decode. Shared models remain under
`app.petitesroues.model`, grouped by responsibility. No actor framework, event bus,
event-sourcing system or general workflow engine is needed.

SQLite transactions provide the write serialization. The initial design keeps the
one-connection pool and does not add a second global command queue. Multi-query
reads explicitly use a consistent transaction; pool size alone is not the read
contract. Business operations do not depend on Java monitor ordering.

## Revisions and storage

Choose exact table and Java names during the first implementation stage. The
required concepts are:

- An accepted document record keyed by local title ID, containing validated JSON,
  a monotonically increasing content revision, and the hash of the exact imported
  source bytes. Preserve the distinction between source-byte hashes and canonical
  JSON hashes.
- A title state version covering changes to its accepted document, pending edits
  and review state. Clients use this when an action depends on displayed state.
- A repository generation covering registration, accepted content publication and
  index replacement. Pending player edits alone should not invalidate a media scan.
- A batch bound to an accepted content revision and an exact set of queue sequences.
- A durable publication receipt identifying the completed batch, request identity,
  imported hash and result. Retrying the same completion returns the same result;
  reusing its identity with different content or decisions is a conflict.

Initially store only the current accepted base, plus the data needed for open
batches and retry receipts. Do not retain an unlimited history of document bodies
or store a second mutable copy of the effective document. Git remains the content
history; SQLite remains the durable application and review state.

## Application operations

### Read a document or derived study result

Within one read transaction, load the accepted document, queue rows and required
batch state. Apply the existing pure document-edit rules and return the resulting
document with its state version. Use the same path for player/reader responses,
speech text, chapter quizzes and phone document export.

Reading accepted content does not access `document.json`. Explicit Refresh detects
missing or changed files. A missing source is reported as availability state;
retain the accepted revision and queued edits so the learner's work remains
recoverable. Media streaming still reads media files and may fail if they disappear.

### Accept a player or reader edit

In one transaction, load the current title state, check the caller's expected
version, apply the command for validation, append it if it changes state, and
advance the title state version. Generate new cue/box IDs once for a logical
request. Define request deduplication for non-idempotent operations such as split
and add, so a lost response cannot create a duplicate on retry.

Return the committed result and version. The browser handles a stale-version
conflict by reloading and asking the learner to reapply an incompatible action;
it must not silently replay a structural edit against different content.

Progress operations validate title/page identity against accepted state in their
transaction. Keep progress separate from content versions: a periodic playback
save must not invalidate a subtitle edit. Define progress-save versus clear
ordering at the client/API boundary so an older in-flight save cannot undo clear.

### Claim a review batch

In one transaction, capture an accepted base revision and exact pending sequences.
Build the batch document from that base and those edits. Preserve later edits
outside the batch. Reopening an existing batch returns the same claim identities.

Record the distinction between the accepted base revision and the title state
version: later player edits do not invalidate completion of the claimed batch.
Keep one active batch per title as a database constraint. Do not imply that this
alone prevents two external agents from writing the same file; the workflow still
requires one file writer, and publication checks reject stale competing results.

### Publish a reviewed document

1. The agent writes and structurally validates its candidate, atomically replaces
   the registered file, and commits/pushes to the existing content upstream.
2. The completion request identifies the batch, expected accepted base revision,
   candidate source hash, outcomes and retry identity.
3. Outside a transaction, the service reads the registered file once, verifies its
   expected hash, decodes it and prepares the immutable candidate. No arbitrary
   caller-supplied path is accepted.
4. In one transaction, verify the registration, base revision, active batch and
   exact claimed sequences. Validate outcomes. Publish the candidate and derived
   metadata, close only the claimed work, replay remaining edits to identify
   conflicts, advance versions and record the receipt.
5. Return the committed result. File changes after candidate capture do not mutate
   this accepted revision; they require another explicit publication.

Later player edits remain queued and are replayed over the accepted candidate.
An unchanged base may close rejected edits, comments or review requests without
creating a new content revision, subject to explicit outcome rules. A changed hash
alone is not evidence that an edit was adopted: define outcome validation and
review obligations before implementation.

Git, file replacement and SQLite cannot form one transaction. Make that separation
recoverable: if publication fails, the old accepted revision and queue remain
active, the file may be ahead, and the agent can retry the same publication. If
the response is lost after commit, the receipt resolves the retry. The server does
not claim to verify Git push success; that remains an agent workflow obligation.

### Add or refresh a repository

Capture the registered repository identity, generation and accepted base revisions
with a short read. Scan, read each candidate into immutable validated data, and
probe outside any transaction. The proposal carries those starting versions and
the actual candidate bytes; the commit does not reread files.

Apply a proposal in one short transaction. Recheck identity, generation, ownership,
duplicate IDs and relevant accepted revisions. Rebase against the latest pending
queue, retain conflicts, then update documents, metadata, missing flags, media and
refresh status together. Reject a stale proposal with an actionable conflict;
do not automatically rerun expensive discovery indefinitely.

A different candidate for a title with an open batch containing document changes
must go through that batch's publication operation. Ordinary Refresh must not
import the batch's edits while leaving them queued for a second replay. Refuse
that repository proposal with guidance to finish or discard its batch. An
unchanged accepted base can refresh normally. Outside an active batch, importing
a changed base preserves and re-evaluates pending edits rather than deleting them.

Each repository is an independent commit. Define Refresh All as a fixed list of
target IDs captured at start, with per-target outcomes. A concurrently removed
repository is reported as skipped; newly added repositories wait for the next
Refresh. Failures remain visible and do not prevent processing unrelated targets.
This deliberately replaces the synchronous endpoint's current fail-fast semantics;
browser and API consumers must migrate together.

Remove a repository in one transaction, refusing removal while unresolved learner
work would be lost unless the learner explicitly discards it first. In-flight
proposals become invalid; applying them cannot insert the registration again.

### Serve phone requests

Give the phone listener a dedicated context/dispatcher that registers only pairing
and authenticated transfer routes. Do not expose the main application's controllers
through that context. Preserve certificate pinning, bearer authentication and
existing phone URLs.

The UI listener remains bound to loopback and independently enforces the actual
peer address and origin policy. Host headers are not proof of a local connection.
Requests on unknown connectors fail closed. Listener status is operational state,
not a decision about which API authority a request receives.

Prototype this separation with the existing embedded server before choosing extra
server machinery. Test context isolation, startup, failed bind, shutdown, port 0
and requests already admitted while closing. Avoid lifecycle locks in request
filters that could block connector shutdown waiting for request completion.

## Migration stages

Each stage should be independently reviewable. Do not remove an old safety boundary
until its replacement is in use and verified.

| Stage | Deliverable | Exit criteria |
| --- | --- | --- |
| 1. Define contracts | Agree invariants, publication semantics, version scope and conflict/retry responses; map every document consumer and mutation | One state/transaction ownership map; no unresolved replay or active-batch publication semantics |
| 2. Separate phone routes | Prototype and implement a phone-only context with stable local-listener admission | Local APIs cannot dispatch from the phone context throughout lifecycle transitions; phone clients still pair and transfer |
| 3. Add accepted state | New additive Flyway migration, accepted document store and explicit recovery/migration command | Existing identities, pending work and decisions preserved; every migrated title has a verified base or explicit recovery status |
| 4. Move reads and edits | Transactional accepted-document reads and versioned edit operations; migrate browser consumers | Player, reader, quiz, speech and phone document reads use accepted state; stale/duplicate edit behavior verified |
| 5. Publish reviews | Atomic publication plus outcomes, later-edit preservation and retry receipts; update agent workflow | Crash/retry scenarios pass; writing a file alone does not change the player; completion publishes exactly one revision |
| 6. Split discovery from commit | Immutable discovery proposals, version checks and per-target Refresh All results | Edits proceed during probing; stale jobs cannot overwrite accepted state or recreate removed repositories |
| 7. Remove obsolete coordination | Remove shared library monitor, redundant service locks and live-file replay paths | Services have explicit transaction boundaries; no production call path depends on the old locking convention |

Stages 3–5 form one behavioral cutover: develop them in slices, but keep ordinary
use on the existing path until accepted reads, edits and review publication work
together. Do not ship a mode that publishes a candidate while still using the old
hash-based replay semantics. Phone isolation can ship independently.

The endpoint inventory completes the initial route/caller map for stage 1. Before
closing that stage, resolve how versions travel through browser application
responses without entering portable JSON, how separately fetched document/queue
state is reconciled, and whether Markdown notes/quizzes retain their current live
file semantics or join explicit publication. The static demo worker is also an API
consumer and must move with any response-contract change.

### Existing-library migration

Do not edit existing Flyway migrations or require users to delete their database.
Add schema first; importing external files is an explicit application migration
operation, not filesystem work hidden inside a Flyway transaction.

Inventory documents, file hashes, queue rows and open batches while mutations are
disabled for the cutover. With an open batch, a changed file may already include
some queued edits. The original base bytes may no longer exist locally. Do not
guess, replay blindly or silently mark work reviewed. Resolve those titles through
the current review protocol before cutover, or retain them in an explicit blocked
recovery state until an agent identifies a safe base. Apply the same caution to
unclaimed edits whose file may have changed independently. Missing or invalid
files retain their queue and identity and receive a recovery status.

Make import resumable and verify command counts, sequences, generated IDs, batch
membership and decisions. Use a schema-version guard rather than a compatibility
reader or runtime fallback to live files. Plan a verified pre-cutover database
backup; after new writes start, rolling back code requires a compatible data
strategy, not simply restoring that old backup and losing later edits.

## Verification

Prefer deterministic barriers at operation boundaries over sleeps or timing-only
assertions. Test the invariants across representative interleavings:

- A document read overlaps batch publication: the result is wholly before or
  wholly after, and a deleted cue never reappears.
- An edit occurs after claim and before completion: it survives publication, with
  a visible conflict if it no longer applies.
- Refresh overlaps edits, publication and repository removal: edits are retained,
  stale proposals are rejected, and removed registrations stay removed.
- A process stops after file replacement, before commit, after commit, or before
  the response arrives: restart and retries preserve exactly one logical outcome.
- Retrying add/split/publication does not duplicate identities or decisions.
- A candidate changes during preparation or has an unexpected hash: publication
  refuses it without partially changing accepted state.
- Source files become missing or invalid: accepted content and learner work remain
  recoverable and availability is reported accurately.
- Phone startup/shutdown and queued requests cannot reach local routes, including
  with forged Host headers or absent Origin headers.
- Progress saves and clears obey their documented ordering without changing
  content versions. Background job shutdown preserves commit/rollback semantics.
- Migration covers unchanged bases, changed files with open batches, later edits,
  missing files and restart midway through import.

Run application transaction tests against real SQLite and HTTP contract tests in
`desktop-server`. Keep pure replay and validation tests in their existing layers.
Browser journeys must use `expectWrite` with meaningful request fields, inspect its
returned JSON, independently verify persisted state with `readJson`, and reload
when verifying restoration, following [web/README.md](../web/README.md#end-to-end-tests).
Run the Java reactor and relevant browser journeys at each behavioral cutover.

## Documentation and review checkpoints

At the relevant cutover, synchronize [DOMAIN-MODEL.md](DOMAIN-MODEL.md),
[REVIEW-API.md](REVIEW-API.md), [java/README.md](../java/README.md), the editing and
database diagrams, API/browser tests, `AGENTS.md` and the
[agent review workflow](../agents/workflows/review-content.md). Document how ordinary
external content changes become accepted and what recovery looks like. Keep
content-specific scripts and data outside tracked application files.

Before implementing stage 3, review the proposed schema and a worked example of
claim → later edit → file publication → retry. Before deleting locks in stage 7,
review the final operation map and prove that no old live-file reconstruction
path remains. The success measure is fewer independent authorities and fewer
coordination rules, with preserved learner work and explicit failure behavior.
