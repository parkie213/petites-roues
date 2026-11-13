# Agent, service and content repository contract

Status: draft for review, 13 November 2026, partly implemented. Implemented: the accepted copy,
import (Add/Refresh, `POST /api/titles/{id}/import`, completion), reads from the accepted copy,
and claim and completion as described below, including the safe retry of a completed batch and the
`prepare/` scripts' open-batch check and import, edits validated and queued in one transaction,
and the removal of the shared library monitor (discovery runs without a lock; its commit is one
transaction), and the skeleton endpoint. Not yet implemented: request IDs for add and split, the
discovery changes for Refresh All and the post-pull trigger. It replaces stage 1 of the state and concurrency plan (on
`codex/endpoint-consolidation`) and decides how content moves.

## Rules

1. **Files are the portable content.** `document.json` files live in a content repository.
   Agents and scripts are their only writers. The service never writes them and never runs Git.
2. **The service serves its accepted copy.** Each title has one accepted document in SQLite.
   Every read (player, reader, quiz, speech, phone export) is that copy plus pending edits, from
   one database snapshot. Reads never open `document.json`.
3. **Import is the only way in.** One operation, *import this registered file*, turns a file
   into the accepted copy. Add, Refresh, a pull, a new title and a review completion are all that
   same operation. Changing a file on disk changes nothing in the app until it is imported.
4. **The queue is the only way out.** Learner edits are commands in SQLite. They reach a file
   when an agent or script claims a batch, writes the file and completes the batch.
5. **Git belongs to whoever writes the file.** A review is committed and pushed before its
   batch is completed. A preparation script imports its own write at once, so review requests
   can name its cues, and the agent commits afterwards. The service does not verify either.

```
content repo files ──import──▶ accepted copy (SQLite) ──▶ served document
        ▲                              ▲
        │                              │ replayed
 agent/script writes ◀──claim batch── pending edits ◀── player/reader
```

## Import

`import(title)` reads the registered file once, outside any transaction, validates it, then in
one transaction:

- applies only if the accepted copy is still the one seen when the read started; otherwise it
  reports a conflict and changes nothing;
- replaces the accepted copy and the catalogue summary derived from it;
- replays pending edits over the new base and marks those that no longer apply as `conflict`;
- with an open batch, also requires that batch's outcomes and closes exactly its edits.

An import of an unchanged file is a no-op. Repeating a completion for a batch that is already
closed with the same file hash returns the stored result. A title with an open batch can only be
imported through that batch's completion; a plain Refresh skips it and says why.

Because the file is written before the import, a crash leaves the file ahead of the app, never
the reverse. The fix is always to run the same import again.

## Journeys

### 1. Add a content repository

The learner registers a folder. The service scans it and reads each `document.json` outside a
transaction, then imports each title. Media links by fingerprint as now. A title whose file is
invalid is listed with its problems and has no accepted copy.

### 2. Adjust subtitle timing

The player sends a timing command; the service validates it against the accepted copy plus
pending edits and queues it in one transaction. The learner sees it at once. It is durable in
SQLite but not yet in the file or Git.

The agent picks them up like any other edit. A batch is one commit, however many edits it
holds: a hundred timing changes are claimed together, written to the file once, committed once
and completed once. The claim keeps its default of 50 edits; when the pending edits are all
timing, the agent asks for a higher limit so they land in one commit. Timing changes need
no annotation work; the agent writes the batch document as given and completes with `applied`.
Batches stay a prefix of the queue, so command order is never rearranged.

### 3. Suggest a subtitle change

The player queues a `suggestion`. An agent claims a batch and receives its changes and the
accepted copy with exactly those edits applied. It reviews, validates the candidate with the
service, writes the file, commits and pushes, then completes the batch. Completion is an import
with outcomes: the new accepted copy and the closed batch commit together. Edits queued after
the claim stay pending and replay over the new copy.

### 4. Pull a change made elsewhere

After `git pull`, files differ from the accepted copies. The app keeps serving what it had until
the changed titles are imported; pending edits are then replayed over the new base and conflicts
are flagged. If the pull touches a title with an open batch, Git reports the merge in the file
the agent is editing, and the batch's completion imports the merged result.

*Deferred while there is one user: who triggers the import after a pull, and whether the app
shows that a file has changed on disk. Until decided, use Refresh.*

### 5. Start a new title

The agent asks the service for a skeleton (`POST /api/titles/skeleton`) for a media file it
already indexes. The service returns the document JSON with the reference fingerprint and an ID
no known title has, and writes nothing. The agent writes it into the content repository, commits
and imports it. `prepare/new_title.py` is this client, so starting a title no longer fingerprints
in Python; `prepare/reference.py` keeps its own fingerprint for documents that already exist.

## Preparation scripts

`prepare/` scripts write `document.json` directly. Under this contract each one refuses to run
while its title has an open batch, and ends by importing the file before queuing review requests
against its cue IDs.

## Upgrading an existing library

The upgrade requires an empty review queue: finish or discard open batches and pending edits
first. Then every accepted copy is simply the current file. Learner progress and title
identities are kept. There is no recovery mode and no reader for the old replay scheme.

## What this makes unnecessary

- the batch file-hash rule that decides whether edits replay over a rewritten file;
- the shared library monitor around discovery (removed);
- client-supplied expected versions on edits (a request ID covers retried split and add);
- a repository generation counter and a separate publication receipt table.

## Out of scope here

Phone route isolation (independent, ships separately), endpoint consolidation, and Markdown
notes and quizzes, which the app only reads and which keep their live-file behaviour.
