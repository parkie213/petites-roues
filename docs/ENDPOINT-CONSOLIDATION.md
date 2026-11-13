# Endpoint overlap and consolidation options

Baseline proposal, 13 November 2026. The five recommended capabilities are now
implemented as 58 routes; see [HTTP-API.md](HTTP-API.md) for the current contract. Source: the complete
[endpoint inventory](ENDPOINT-OPERATIONS.md), at workspace commit `27962b2`.
The historical **80 method/path routes** appear once
below, grouped by capability rather than controller. Suggested URLs below preserve the design discussion; current contracts are linked above.

## Recommended direction

| Capability | Current routes | Proposed routes | Value and recommendation |
| --- | ---: | ---: | --- |
| Document edits and feedback | 13 | 1 | Strong candidate: one typed-command acceptance/version/retry protocol |
| Learner progress | 6 | 3 | Strong candidate: one title progress resource for both media kinds |
| Discovery and job lifecycle | 7 | 4 | Strong candidate: retire 3 synchronous discovery adapters after migrating callers |
| Review work management | 7 | 5 | Remove exact alias; combine pending/history listings while preserving lifecycle operations |
| Document loading/export/validation | 4 | 2–3 | One coherent study-state read; optional raw export representation; keep candidate validation |
| Library browsing/matching | 7 | 5–7 | Potentially replace repository/flat-title subset reads with canonical library snapshot |
| Artwork | 8 | 2 | Optional: transport consolidation; preserve versioned caching |
| Speech and voices | 7 | 6 | Optional: one local synthesis request; settings/catalogue/sample operations stay distinct |
| File chooser, media resources, notes/quizzes and phone routes | 21 | 21 | Keep initially; these do not share one meaningful operation |

These counts are alternatives, not an endpoint-count target. The first five rows
would reduce 37 routes to 15–16 and the full surface from 80 to **58–59**. Taking all
optional reductions would produce **49–52** routes. The larger reductions mainly
compress transport representations; they are not all equivalent reductions in
application complexity. No reduction justifies losing a validation, atomicity,
identity, caching or authorization guarantee.

## Full list by capability

“Keep” means retain the capability in this design pass, not that its current
implementation is already correct. Existing callers must move as part of each
contract cutover. External scripts may exist beyond this repository.

### Library browsing and matching — 7 routes

GET library already includes repository summaries, title entries and media. GET
repositories and GET library/titles expose subsets with different shapes. They can
be removed if preparation and test consumers use that snapshot. This saves two
routes, but could transfer unnecessary media data to simple lookups. Keep a thin
projection if measured payload/caller needs justify it. Poster shelves contain
server-owned grouping rules; folding that endpoint into a browser transformation
would move domain logic into the UI. Disk measurement and media-link writes are
separate operations.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/library` | Keep distinct projection or operation |
| GET | `/api/library/posters` | Keep distinct projection or operation |
| GET | `/api/library/titles` | Candidate: derive from canonical library snapshot; evaluate payload cost |
| GET | `/api/library/usage` | Keep distinct projection or operation |
| PUT | `/api/library/media-links` | Keep distinct projection or operation |
| DELETE | `/api/library/media-links` | Keep distinct projection or operation |
| GET | `/api/repositories` | Candidate: derive from canonical library snapshot; evaluate payload cost |

### Repository discovery and jobs — 7 routes

The three synchronous Add/Refresh routes duplicate commands available through
POST jobs. Current browser management uses jobs; the old Repositories component
is unreferenced. Move integration fixtures and external tools to start-and-await
jobs, then retire those three routes. Keep repository removal distinct. Starting,
resuming observation of the latest job, and polling a known job are useful separate
contracts. A differently requested command must not silently be reported as accepted
just because another job is already running.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| POST | `/api/jobs` | Keep; make job admission/result identity explicit |
| GET | `/api/jobs/latest` | Keep; make job admission/result identity explicit |
| GET | `/api/jobs/{id}` | Keep; make job admission/result identity explicit |
| POST | `/api/repositories` | Retire after callers move to jobs |
| POST | `/api/repositories/refresh` | Retire after callers move to jobs |
| POST | `/api/repositories/{id}/refresh` | Retire after callers move to jobs |
| DELETE | `/api/repositories/{id}` | Keep; make job admission/result identity explicit |

### Local file chooser — 1 routes

One local-only interaction, already parameterized by folder/media choice. No
reason to fold it into discovery: choosing a path does not authorize registration.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| POST | `/api/files/choose` | Keep: resource identity or local interaction differs |

### Document loading, export and validation — 4 routes

GET title and GET comic both return a StudyDocument, but comic open first checks
its archive. Replace them with one application study-state response containing the
portable document, versions, progress, pending-review summary and media problems.
Capture database state together; check external archive availability afterward and
bind that result to the captured manifest. Decide whether a broken/missing archive
is an error or a reported media problem without silently removing today's checks.

The explicit export route only adds download delivery behavior and has no current
production source caller. If retained, implement it as a documented raw-document
representation of the same read (for example format=document and download=true),
with no application envelope/local fields in portable JSON. This is an optional
route simplification, not evidence that raw export needs no separate contract.
Candidate validation stays separate: it checks caller bytes without publishing.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/titles/{id}/comic` | Merge into one study-state read; preserve comic archive checks |
| GET | `/api/titles/{id}` | Merge into one study-state read; preserve comic archive checks |
| POST | `/api/titles/{id}/validate` | Keep: candidate inspection does not publish |
| GET | `/api/titles/{id}/export` | Optional retirement: raw download representation of canonical document read |

### Document edits and feedback — 13 routes

All thirteen routes ultimately create DocumentEdit variants. A common command
endpoint is a credible improvement because version checking, request deduplication,
transaction ownership and committed response shape need to be identical across
these writes. Preserve named command types and their validators; this proposal is
not arbitrary JSON patching or a stringly typed map passed into storage.

Suggested shape: POST /api/titles/{id}/commands with expectedVersion, requestId and
a bounded commands array. Validate every command and commit the whole request or
none. One timing command still moves its requested neighbours atomically; multiple
review-request commands retain the existing all-or-none preparation behavior.

The persisted DocumentEdit is not automatically the public input DTO: the server
must assign IDs for add/split/create and fix them in the stored command. Reject
client attempts to choose server-owned identities. Return generated IDs explicitly,
along with stateVersion and committed document/review state, instead of having the
comic client infer new boxes by diffing the response. A comment can advance state
without changing document bytes. Keep review publication/discard out of this endpoint.

This revises the earlier inventory's conservative recommendation to keep separate
edit URLs: typed domain operations should remain, but they need not each own a URL.
The benefit is one acceptance protocol; a switch moved behind one URL alone would
not simplify the application.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| PUT | `/api/titles/{id}/comic/lines/{lineId}` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/comic/boxes` | Merge into POST title commands; preserve typed operation and payload rules |
| PUT | `/api/titles/{id}/comic/boxes/{boxId}/placement` | Merge into POST title commands; preserve typed operation and payload rules |
| DELETE | `/api/titles/{id}/comic/boxes/{boxId}` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/review/requests` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/timings` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/cues/{cueId}/suggestion` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/cues/{cueId}/comment` | Merge into POST title commands; preserve typed operation and payload rules |
| PUT | `/api/titles/{id}/cues/{cueId}` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/cues` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/cues/{cueId}/split` | Merge into POST title commands; preserve typed operation and payload rules |
| POST | `/api/titles/{id}/cues/merge` | Merge into POST title commands; preserve typed operation and payload rules |
| DELETE | `/api/titles/{id}/cues/{cueId}` | Merge into POST title commands; preserve typed operation and payload rules |

### Review work management — 7 routes

Remove changes outright after checking external usage: it is an exact alias.
Pending and history can share GET /api/titles/{id}/review?status=pending|completed.
They are one queue/history projection, although pending additionally computes replay
state. Do not fetch unbounded history in every study-state response. The application
read includes the summary needed by the player, so opening the player does not
require a second independently timed queue request.

Keep cross-title pending-work discovery, claiming, completing/publication and discard
as distinct operations. They have different preconditions and durable effects.
Combining those into action=... would reduce route count but hide the review state
machine. A status filter on a listing is a smaller, coherent consolidation.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/review/pending` | Keep separate lifecycle operation |
| GET | `/api/titles/{id}/review-queue` | One review listing with explicit status filter |
| GET | `/api/titles/{id}/changes` | Remove redundant alias |
| GET | `/api/titles/{id}/review-history` | One review listing with explicit status filter |
| POST | `/api/titles/{id}/review/batches` | Keep separate lifecycle operation |
| POST | `/api/titles/{id}/review/batches/{batch}/complete` | Keep separate lifecycle operation |
| POST | `/api/titles/{id}/review-queue/discard` | Keep separate lifecycle operation |

### Learner progress — 6 routes

Video and comic each expose GET/PUT/DELETE for the same title-owned progress
resource. Use /api/titles/{id}/progress for both. The title determines the applicable
position type; the model validates seconds for video and stable page ID for comic.
Use a typed response/body rather than accepting both fields ambiguously. Progress
ordering/version remains separate from content version. Include progress in the
initial study-state response to remove the extra opening request; retain explicit
progress reads for independent persisted-state checks and other consumers.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/titles/{id}/comic/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |
| DELETE | `/api/titles/{id}/comic/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |
| PUT | `/api/titles/{id}/comic/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |
| GET | `/api/titles/{id}/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |
| DELETE | `/api/titles/{id}/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |
| PUT | `/api/titles/{id}/progress` | Unify video/comic path; retain GET, PUT and DELETE semantics |

### Video and comic resources — 7 routes

Title media lookup and fingerprint media lookup have different inputs: one chooses
a linked copy, the other opens bare media without a study document. Annotated comic
pages address stable page IDs; bare CBZ pages use archive order. Do not merge those
identities by interpreting an untyped id parameter.

A later design could make library/study-state responses advertise resolved resource
URLs so consumers no longer construct title-relative media addresses. That requires
explicit resource identity and invalidation rules, not just fewer controller methods.
Keep these seven routes for the initial migration. Version redirects can be replaced
with version query parameters, but that changes URL shape more than domain logic.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/titles/{id}/comic/pages/{pageId}` | Keep: resource identity or local interaction differs |
| GET | `/api/media/{fingerprint}/pages` | Keep: resource identity or local interaction differs |
| GET | `/api/media/{fingerprint}/pages/{index}` | Keep: resource identity or local interaction differs |
| GET | `/api/titles/{id}/media` | Keep: resource identity or local interaction differs |
| GET | `/api/titles/{id}/media/{version}` | Keep: resource identity or local interaction differs |
| GET | `/api/media/{fingerprint}/video` | Keep: resource identity or local interaction differs |
| GET | `/api/media/{fingerprint}/video/{version}` | Keep: resource identity or local interaction differs |

### Library artwork — 8 routes

Eight routes represent title/media identity × card/series-card choice ×
current/versioned delivery. There is genuine transport repetition here, although
CardController already shares redirect/serve code.

An optional two-route design is GET /api/titles/{id}/art?kind=card|series&v=... and
GET /api/media/{fingerprint}/art?kind=card|series&v=.... Omitting v resolves/redirects
to the current version. Keep the version in the cache key; only a matching version
gets immutable caching, and an unversioned or stale request must resolve afresh.
This saves six explicit routes but does not remove eight resource representations.
Do it only if the common resolver/controller contract is clearer. A separate asset
registry solely to achieve one URL would add more machinery than it removes.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/titles/{id}/card` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/titles/{id}/card/{version}` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/titles/{id}/series-card` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/titles/{id}/series-card/{version}` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/media/{fingerprint}/card` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/media/{fingerprint}/card/{version}` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/media/{fingerprint}/series-card` | Optional representation consolidation; cache semantics must remain explicit |
| GET | `/api/media/{fingerprint}/series-card/{version}` | Optional representation consolidation; cache semantics must remain explicit |

### Study notes and quiz preparation — 3 routes

Notes and quizzes are already two thin adapters over MarkdownPageService.pages(kind).
One GET pages?kind=notes|quizzes could replace their URLs, but would save almost no
logic. Keep them until deciding their publication policy. Chapter quiz preparation
is a pure computation from accepted comic content and caller rules, not a Markdown
listing or persistent write; keep its distinct contract.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| POST | `/api/titles/{id}/comic/quiz` | Keep for now: shared reader is already present |
| GET | `/api/quizzes` | Keep for now: shared reader is already present |
| GET | `/api/notes` | Keep for now: shared reader is already present |

### Speech and voices — 7 routes

Title speech and installed-voice preview both call the same synthesizer. One local
POST /api/speech request could accept typed context: titleId for source-language and
saved-voice resolution, or explicit language/voice for settings preview, plus text.
That saves one route and one synthesis transport contract. It requires switching
browser audio handling from a directly constructed GET URL to a fetched audio blob.
Retain the distinction between upstream comparison samples and generated speech;
provider MP3 is not equivalent to local synthesis. Catalogue, installed voices,
selection and installation also have different ownership and costs.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/titles/{id}/speech` | Candidate: one synthesis operation with explicit title/language context |
| GET | `/api/speech/voices` | Keep: distinct settings/catalogue/install/sample operation |
| PUT | `/api/speech/voices/{language}` | Keep: distinct settings/catalogue/install/sample operation |
| GET | `/api/speech/voices/catalogue` | Keep: distinct settings/catalogue/install/sample operation |
| POST | `/api/speech/voices/catalogue/{id}/install` | Keep: distinct settings/catalogue/install/sample operation |
| GET | `/api/speech/voices/catalogue/{id}/sample` | Keep: distinct settings/catalogue/install/sample operation |
| GET | `/api/speech/voices/{language}/preview` | Candidate: one synthesis operation with explicit title/language context |

### Local phone administration — 5 routes

Listener status/configuration, pairing-secret creation, paired-device listing and
revocation are different operations. Combining them into one settings document
would blur volatile lifecycle state and durable credential changes. Keep their
contracts; route isolation is more valuable than shortening this list.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| GET | `/api/phone` | Keep: different lifecycle, credential or configuration operation |
| PUT | `/api/phone` | Keep: different lifecycle, credential or configuration operation |
| POST | `/api/phone/pairings` | Keep: different lifecycle, credential or configuration operation |
| GET | `/api/phones` | Keep: different lifecycle, credential or configuration operation |
| DELETE | `/api/phones/{id}` | Keep: different lifecycle, credential or configuration operation |

### Phone protocol — 5 routes

These URLs are a separate authenticated phone protocol. A phone title offer includes
transfer paths, byte counts and fingerprints, unlike the browser library projection.
Share application readers and serialization underneath, but do not expose local
administration endpoints on the phone listener to reuse their URLs. Reassess hello
only if phone requirements change; it currently provides an explicit connection test.

| Method | Current path | Proposed disposition |
| --- | --- | --- |
| POST | `/phone/pair` | Keep phone-only authority; share underlying read/serialization operations |
| GET | `/phone/hello` | Keep phone-only authority; share underlying read/serialization operations |
| GET | `/phone/pages/{repository}/{folder}/{name}` | Keep phone-only authority; share underlying read/serialization operations |
| GET | `/phone/titles` | Keep phone-only authority; share underlying read/serialization operations |
| GET | `/phone/titles/{id}/{role}` | Keep phone-only authority; share underlying read/serialization operations |

## Worked command example

A cue split can use this application request (illustrative field names):

```json
{
  "expectedVersion": 42,
  "requestId": "learner-generated-request-uuid",
  "commands": [
    {
      "type": "split-video-cue",
      "cueId": "cue-17",
      "at": 12.5,
      "first": "Bonsoir !",
      "second": "Comment vas-tu ?"
    }
  ]
}
```

The acceptance service checks the version and split rules, assigns the new cue ID,
queues the resulting command and advances state in one transaction. It returns the
committed state/version and created IDs. A retry with the same request identity and
payload returns the recorded result; a different payload with the same identity is
a conflict. The same envelope supports comic commands and feedback with their own
typed fields. It does not accept raw persisted commands containing generated IDs.

## Proposed order

1. Agree the command envelope, study-state response and common progress model.
   These change the boundaries that matter to the migration, not just URL naming.
2. Make jobs the one discovery acceptance contract and move synchronous callers.
   Specify identity, conflict and per-repository outcomes before removing adapters.
3. Consolidate review listings and retire the exact alias with the caller cutover.
4. Decide whether export and thin catalogue reads justify independent routes.
5. Defer artwork and speech transport cleanup unless it makes the changed clients
   simpler. Do not block accepted-state migration on optional URL compression.

The original controller inventory remains the record of current behavior. This
proposal supersedes its assumption that typed commands necessarily need separate
edit endpoints. Keep validators/domain commands and remove duplicated acceptance
protocols. No generic JSON mutation API, new module or MCP adapter is proposed.

## Verification

The list was compared against the 80 routes in ENDPOINT-OPERATIONS.md: each current
method/path occurs exactly once, and each belongs to one capability group. The
counts above are checked against those groups. Documentation links and whitespace
were checked. No application behavior changed, so runtime suites were not rerun.
