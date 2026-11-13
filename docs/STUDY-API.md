# Study state and document representations

`GET /api/titles/{id}` is the one title read URL:

| Request | Representation |
| --- | --- |
| Default, or `?view=document` | Portable StudyDocument JSON |
| `?view=study` | Application state: `{document, version, pending, progress}` |
| `?download=true` | Portable document with an attachment filename |

The former `/comic` opening and `/export` URLs are retired. Comic page-image URLs
remain under `/comic/pages/{pageId}`. Candidate validation remains
`POST /api/titles/{id}/validate`; it writes nothing and does not reserve a version.
Unknown views and downloading the application-state representation return 400.

The study representation captures the document, pending review state and resume
position in one application read. Its version is also returned in `X-State-Version`.
Players use this representation instead of separately fetching a document, queue
and progress. Comic opening still validates the captured manifest against its
archive after the state transaction. Missing archives and manifest entries retain
explicit errors; this consolidation does not silently bypass that check.

Raw and downloaded documents contain no local versions, review rows or progress.
Agents, phone export and portable-file workflows continue using portable JSON.
The static demo implements the same study-state representation for its canned
content and persisted demo progress.

Progress for both media kinds uses `/api/titles/{id}/progress`:

- GET returns saved state (`positionSeconds` for video, `pageId` for comic).
- PUT accepts exactly one numeric `positionSeconds` or string `pageId`, validated
  against the title's media kind. Ambiguous or mismatched positions return 400.
- DELETE clears that title's saved position, leaving content and review state alone.

Progress is independently readable for persistence checks. Its updates do not
change the content/review version. Server transactions own current save/clear
ordering; coordinating a card clear with another browser's in-flight save remains
an explicit state-migration concern.

See [COMMAND-API.md](COMMAND-API.md), [REVIEW-API.md](REVIEW-API.md) and
[DISCOVERY-API.md](DISCOVERY-API.md) for the other consolidated capabilities.
Accepted-document storage and explicit agent publication remain proposed in
[STATE-AND-CONCURRENCY-PLAN.md](STATE-AND-CONCURRENCY-PLAN.md). This work changes
application boundaries without claiming that external file writes are transactional.
