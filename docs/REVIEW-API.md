# Review API

How player edits reach the document files. The application never writes `document.json`:
it queues edits and serves each document as the file plus its pending edits. An agent (or a
person) is the only writer, and takes edits in batches.

```
player edit ──▶ review_queue (SQLite, the edit as a command)
                    │
GET document ◀── file + pending commands replayed
                    │
agent: claim batch ──▶ write document.json ──▶ commit/push ──▶ complete batch
```

## Guarantees

- Every player and reader change (text, timing, notes, splits, merges, deletions,
  suggestions, comic boxes and text) is stored as a command with any IDs it created. An edit
  that changes nothing is not queued.
- Reads replay pending commands in order, through the same code that accepted them.
- A command that no longer applies to the file (its cue was removed or renamed, a comic
  bubble's text changed since) is skipped and reported as `conflict`.
- A batch records the SHA-256 of the file when claimed. Its commands are replayed only while
  the file still has that hash, so once the agent writes the file they are not applied twice.
- Edits made after a claim are not in the batch. They keep replaying over whatever the agent
  writes, and go in a later batch.
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
| `POST /titles/{id}/review/batches/{batch}/complete` | Record outcomes and close the batch |
| `POST /titles/{id}/review-queue/discard` | Body: the displayed pending sequences. Drops those edits, reverting them in the player |
| `POST /titles/{id}/review/requests` | Body: `[{"cueId":"cue-0001","text":"what to check"}]`. Queues review requests, all or none; returns `{"queued":n}` |
| `POST /titles/{id}/validate` | Body: a candidate `document.json`. Returns `{"problems":[...]}`, each prefixed with its cue ID where it has one; empty means the file would load. Writes nothing |

A queue row has `sequence`, `timestamp`, `kind` (timing, suggestion, comment, review-request,
edit, add, delete, split, merge, annotate), `cueIds`, `suggestion` (the text of a suggestion,
comment or review request), `batch`, `state` and `command`. A `comment` is feedback from the
player (`POST /titles/{id}/cues/{cueId}/comment`) and a `review-request` is machine output to
check, queued by a preparation script; neither changes anything in the document. States:

| State | Meaning |
| --- | --- |
| `pending` | Replayed over the file |
| `conflict` | No longer applies to the file; not replayed. Decide by hand |
| `written` | Its batch's file has since been rewritten; awaiting completion |
| `review` | Queued before edits were replayed; the change is already in the file |

A claimed batch returns `{batch, baseSha256, changes, document}`. `document` is the file plus
exactly the batch's edits, in the same JSON as the document files.

Completion body: `{"outcome":"applied","outcomes":{"12":"not_adopted"}}`. `outcome` defaults to
`applied` and covers every edit not listed in `outcomes`. Outcomes are `applied`,
`already_correct`, `not_adopted` and `reviewed`.

The agent procedure is [agents/workflows/review-content.md](../agents/workflows/review-content.md).
