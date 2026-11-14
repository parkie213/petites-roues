# Review API

How player edits reach the document files. The application never writes `document.json` and
never reads it to answer a request: it serves each title's served copy: the accepted copy (the file as last
imported, kept in SQLite) with its pending edits applied, stored so that a read is a lookup. An agent (or a person) is the only writer, and
takes edits in batches. A file changes what the app serves only when it is imported.

```
player edit ──▶ review_queue (SQLite, the edit as a command)
                    │
GET document ◀── served copy (accepted copy + pending commands, updated by every write)
                    │
agent: claim batch ──▶ write document.json ──▶ commit/push ──▶ complete batch (imports the file)
```

## Guarantees

- Every player and reader change (text, timing, notes, splits, merges, deletions,
  suggestions, comic boxes and text) is stored as a command with any IDs it created. An edit
  that changes nothing is not queued.
- Every transaction that changes the accepted copy or the queue (an edit, completion, discard,
  import) leaves the served copy equal to the pending commands replayed in order over the
  accepted copy, through the same code that accepted them. A read does not replay.
- A command that no longer applies to the accepted copy (its cue was removed or renamed, a comic
  bubble's text changed since) is skipped and reported as `conflict`. The rebuild that replays
  the queue (after a completion, discard or import) decides this and stores it with the row.
- The served copy and the pending edits are read in one database snapshot, so a read sees a
  document wholly before or wholly after a completion. A title with no accepted copy fails with a
  409 asking for a Refresh of its folder.
- A claim records the accepted copy's SHA-256 as the batch's base. Its commands stay in the served
  copy until the batch is completed; writing the file does not change what is served.
- Completing a batch imports the registered file as the new accepted copy (and updates the title's
  catalogue metadata) and closes the batch's edits in one transaction: both happen or neither.
  The served copy is rebuilt in the same transaction, even when the file is unchanged, so the
  closed edits leave it. The file is read once, before the transaction. If the accepted copy is no
  longer the batch's base the completion fails with a 409 and changes nothing.
- Completing a batch that is already closed succeeds when the file is still the accepted copy, so a
  lost response can be retried. Otherwise it fails with "No open batch".
- Edits made after a claim are not in the batch. The completion replays them over the new accepted
  copy, with fresh `conflict` states, and they go in a later batch.
- A batch cannot be completed while the file is unchanged since the claim, unless every edit
  in it is `not_adopted`, a comment or a review request.
- Removing a repository is refused while its titles have edits that aren't in the files yet.

## Endpoints

All are under `/api` and return `Cache-Control: no-store`. Title IDs are local catalogue IDs.

| Request | Purpose |
| --- | --- |
| `GET /review/pending` | Titles with pending rows, oldest work first, and any open batch |
| `GET /titles/{id}/review-queue` | Pending rows with their `state` and `command` |
| `GET /titles/{id}/review-history` | Completed rows and their outcomes |
| `POST /titles/{id}/review/batches?limit=50` | Claim the oldest pending edits, or return the open batch; `204` when nothing is pending |
| `POST /titles/{id}/review/batches/{batch}/complete` | Import the file as the accepted copy, record outcomes and close the batch |
| `POST /titles/{id}/import` | Import the registered `document.json` as the accepted copy. Returns `{"result":"imported"}` or `{"result":"unchanged"}`; `409` while a batch is open (complete or discard it first) or if the accepted copy changed meanwhile; `404` for an unknown title |
| `POST /titles/{id}/review-queue/discard` | Body: the displayed pending sequences. Drops those edits, reverting them in the player |
| `POST /titles/{id}/review/requests` | Body: `[{"cueId":"cue-0001","text":"what to check"}]`. Queues review requests, all or none; returns `{"queued":n}` |
| `POST /titles/skeleton` | Body: `{"media":"/abs/path","title":"…"}` plus optional `id`, `series`, `volume` (comics), `sourceLanguage` (`fr`), `translationLanguage` (`en`), `notesLanguage` (`en`), `leftToRight` (comics), `ignoreTrackLanguage`. Returns the canonical JSON of an empty document for a video or CBZ already in the media index (a video with no cues; a comic with its pages and no boxes). Writes nothing: save it in the content repository, then import it. `400` for a file not in the index or changed since its refresh, a video with no audio or whose first track is tagged with another language than `sourceLanguage`, a CBZ with no images, a volume on a video or a missing title; `409` when a title already has the document ID |
| `POST /titles/{id}/validate` | Body: a candidate `document.json`. Returns `{"problems":[...]}`, each prefixed with its cue ID where it has one; empty means the file would load. Writes nothing |

A queue row has `sequence`, `timestamp`, `kind` (timing, suggestion, comment, review-request,
edit, add, delete, split, merge, annotate), `cueIds`, `suggestion` (the text of a suggestion,
comment or review request), `batch`, `state` and `command`. A `comment` is feedback from the
player (`POST /titles/{id}/cues/{cueId}/comment`) and a `review-request` is machine output to
check, queued by a preparation script; neither changes anything in the document. States:

| State | Meaning |
| --- | --- |
| `pending` | In the served copy, replayed over the accepted copy |
| `conflict` | No longer applies to the accepted copy; left out of the served copy. Decide by hand |
| `review` | Queued before edits were replayed; the change is already in the document |

A claimed batch returns `{batch, baseSha256, changes, document}`. `document` is the accepted copy
plus exactly the batch's edits, in the same JSON as the document files. When the batch holds every
pending edit it is the served copy; otherwise it is replayed for the claim.

Completion body: `{"outcome":"applied","outcomes":{"12":"not_adopted"}}`. `outcome` defaults to
`applied` and covers every edit not listed in `outcomes`. Outcomes are `applied`,
`already_correct`, `not_adopted` and `reviewed`.

The agent procedure is [agents/workflows/review-content.md](../agents/workflows/review-content.md).
