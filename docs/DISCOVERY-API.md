# Library discovery jobs

Add and Refresh use `POST /api/jobs`. The synchronous repository Add/Refresh URLs
are retired. Listing (`GET /api/repositories`) and removal
(`DELETE /api/repositories/{id}`) remain separate.

Submit one command:

```json
{"kind":"add","localPath":"/absolute/content/path","name":"Optional name"}
```

```json
{"kind":"refresh","repositoryId":"optional-single-repository-id"}
```

The accepted response is HTTP 202 with a job ID. Poll `GET /api/jobs/{id}` to a
terminal state. `GET /api/jobs/latest` restores observation after a page reload
(204 before the first job). Only the latest job is retained in memory.

An identical command submitted while running returns that job; a different
command returns 409 and is not accepted. A lost response after completion does not
provide durable job deduplication: check the repository state before submitting a
new Add. Job results are operational state, not a durable queue.

A terminal job includes `outcomes`, each containing repositoryId, state
(`succeeded`, `failed` or `skipped`), the committed repository summary when
successful, and an error when applicable. Refresh All captures its target IDs at
admission, processes them individually, and continues after a target fails. A
removed target is skipped. Any failed target makes the overall job failed while
retaining the successful targets' outcomes. Add failure is reported by job.error.

Await completion before dependent work and independently read the repository or
library state to verify persistence. HTTP 202 alone confirms admission only.
Discovery still uses the existing shared update monitor; moving scanning outside
that boundary and applying revision-checked proposals belongs to the state migration.
