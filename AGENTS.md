# Language learning workspace

## Browser journey assertions

Every journey must assert the intended effect of its requests: rendered domain state for
reads, and successful persistence plus the resulting UI or state for writes. Use `expectWrite`
from `web/e2e/api.mjs` around save actions: specify the method, path and meaningful request
fields, then check its returned JSON. Await completion before navigation or the next dependent
action. Visibility of optimistic UI or a 2xx response alone is not proof of persistence: use
`readJson` for an independent persisted-state check, and reload when testing restoration.

The shared request guard rejects unsuccessful API responses and failed/incomplete finite requests
in recording and replay. It is a backstop, not a replacement for domain assertions. Only aborted
image, media and event-stream reads are exempt from completion checks; their HTTP and transport
errors still fail. JSON reads and writes must complete. Keep write payloads in recordings so
replay cannot accept a different command at the same URL.
Do not suppress abandoned writes or use sleeps to make recordings stable. Video journeys that
need fixed positions use `pauseOnOpen`; the browser's gesture policy still allows muted autoplay.
See `web/README.md#end-to-end-tests` for the assertion pattern and journey coverage.

## Java naming and responsibility conventions

- `...Service` contains application logic and coordinates operations. Services may call stores,
  but must not contain SQL, JDBC calls or database row mapping. Transaction boundaries belong
  in services because they define which operations succeed or fail together.
- `...Store` contains database queries, persistence mapping and writes. It does not discover
  content or decide application workflows.
- `...Scanner` traverses directories and discovers content; it does not access the database.
- `...Reader` reads and decodes a particular file format.
- `...Controller` handles HTTP requests and responses and delegates application operations.
- Shared model types live under `app.petitesroues.model`, grouped by responsibility. Model names
  describe the data or intrinsic domain rules they represent.

Class and method Javadoc should explain purpose, ownership and material guarantees so callers
can understand the contract without tracing the implementation. Use normal imports instead of
fully qualified annotations and types unless qualification resolves an actual name collision.

## Language pairs

Documents declare `sourceLanguage`, `translationLanguage` and `notesLanguage` using
BCP 47 tags. Use these roles consistently in app code and content workflows.
French–English examples illustrate the teaching standard; adapt explanations to the
actual source language and write notes in the declared notes language.

## Translation and learner notes

When creating, reviewing or updating subtitle translations and learner notes for any film/show, first read [the translation notes guide](agents/guides/translation-notes.md) and follow its teaching and review standard.

The notes must explain why the source text has its contextual meaning, especially when a familiar word changes meaning in a pronominal verb or idiom. A translation plus a word-order rule is not sufficient for such a line.

Keep canonical annotation sources and generated/live outputs synchronized. Check title-specific files and scripts rather than assuming a fixed layout. Preserve original media and subtitle references, and use Git checkpoints for annotation revisions.

## Live annotation reviews

The app never writes document files. Player and reader edits are queued in SQLite and replayed
over `document.json` when it is served; agents are the only writers. To review, first read
[the translation notes guide](agents/guides/translation-notes.md), then follow
[agents/workflows/review-content.md](agents/workflows/review-content.md): claim a batch, write its
document into the registered file with the corresponding translations, notes, checks and
alignments, commit and push to the existing content upstream, then complete the batch. Never edit
queue rows directly. Retain unresolved listening checks; a subtitle correction is not audio
verification. Routine reviews require no exports or backups.

## Preparing titles

Follow [docs/PREPARE-TITLE.md](docs/PREPARE-TITLE.md): `prepare/new_title.py` starts an empty
document that references the media, then OpenRouter-backed scripts in `prepare/` fill in
transcription, comic text and first-pass notes. Keep it a
runbook with small scripts, not a pipeline. Use [agents/workflows/subtitle-quiz.md](agents/workflows/subtitle-quiz.md)
for interactive translation practice, and [agents/workflows/write-quiz.md](agents/workflows/write-quiz.md)
for general quizzes (Markdown files in a content repository's `quizzes/` folder), and
[agents/workflows/write-notes.md](agents/workflows/write-notes.md) for study notes (its `notes/`
folder). Preserve raw model output, original subtitles and source
references in `content-archive/`.

Keep this repository content-agnostic. Local catalogues, media, subtitle/annotation packs
and title-specific preparation scripts belong outside the tracked application files.

Local content belongs under ignored `content/`, or in a separately registered content repository.
Live documents may be in that repository; videos and SQLite stay local.
The SQLite index stores document paths relative to their owning repository root.
New titles are started with `prepare/new_title.py` and indexed through repository Add/Refresh; the SQLite location is configured independently.
See [the document format](docs/DOCUMENT-FORMAT.md#documents-and-media). Keep app sources and generic workflows outside content.

Keep `content/` limited to live videos, comics, subtitle/notes documents and library
metadata. Preparation scripts, research Markdown, old sources and exports belong in
ignored `content-archive/`, not among live title files.

The local catalogue is `content/library.sqlite` (SQLite), not binding or organization
JSON. Documents remain JSON. Repository-wide title discovery runs only on explicit
Add/Refresh actions; normal browsing uses SQLite, and media links to a document by
fingerprint (its `reference`). Do not add compatibility readers.

Git is an ordinary checkpoint for content, not the review queue. Pending player edits and
completed review decisions are in SQLite ([docs/REVIEW-API.md](docs/REVIEW-API.md)). Old JSONL
is archived only; do not import its pending work into the new queue.
