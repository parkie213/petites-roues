# Document commands

`POST /api/titles/{id}/commands` accepts typed video/comic edits and review feedback.
It replaces the individual timing, cue, comic-line/box and review-request write URLs.
The application queues commands; agents still own portable document files.

First read the title and retain its `X-State-Version` response header. Submit:

```json
{
  "requestId": "a-client-generated-uuid",
  "expectedVersion": "the-opaque-version-returned-by-the-read",
  "commands": [
    {"type": "comment", "cueId": "cue-17", "text": "Please check this wording"}
  ]
}
```

The response is current portable document JSON, with `X-State-Version`,
`X-Accepted-Version` (the state this logical request originally committed) and
`X-Created-Ids` (comma-separated server-assigned identities). Versions are local
application metadata, never fields in portable documents. Comments and review
requests change the version even when the document bytes do not change.

A request contains 1–1000 commands, validated and stored all or none. Unsupported
commands or invalid fields fail validation. An outdated expected version returns
409; reload before applying a new logical request. Retry a lost/failed transport
with the exact same request ID and body: the compact durable receipt returns the
original accepted version and assigned IDs without applying commands again. The
document and X-State-Version reflect current state, including later edits. Reusing an ID for a different body returns 409. Receipts
last for the registration's lifetime and are removed with the title.

| Type | Input fields |
| --- | --- |
| `timing` | `edits`: timing entries `{id,start,end}` |
| `suggestion` | `cueId`, `text` |
| `comment` | `cueId`, `text` |
| `review-request` | `cueId`, `text` |
| `edit-video-cue` | `cueId`, `edit`: existing video cue edit fields |
| `add-video-cue` | `edit` |
| `split-video-cue` | `cueId`, `split`: `{at,first,second,start?,end?}` |
| `merge-video-cues` | `ids`: consecutive cue IDs; server orders by playback |
| `delete-video-cue` | `cueId` |
| `edit-comic-text` | `lineId`, `edit`: `{expectedSource,source}` |
| `create-comic-box` | `create`: `{pageId,region,source?}` |
| `place-comic-box` | `boxId`, `update`: `{expectedRegions,regions,...}` |
| `delete-comic-box` | `boxId` |

Server-generated cue, box and line identities cannot be selected through the input
commands. Structural edits retain existing domain validation, annotation invalidation
and listening/text-check behavior. Multiple review requests use multiple typed
commands in one batch. Publication and discard remain separate review operations.

This endpoint consolidation retains live-file serving until the planned accepted
state migration. Its opaque version binds the captured source hash and pending
queue state; it is not a promise that external file writers participate in SQLite
transactions. Use the registered content workflow for file publication.
