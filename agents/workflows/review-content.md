# Process the review queue and checkpoint content

Use when asked to process a title's review queue. Read [translation-notes.md](../guides/translation-notes.md)
first. The app never writes document files. It queues player edits and serves each document as the
file plus those edits. Preparation scripts queue their first-pass output here too, as review
requests. You are the only writer: take the edits in batches, write them into the
file with their annotations, commit and push, then complete the batch. The API is described in
[docs/REVIEW-API.md](../../docs/REVIEW-API.md).

1. `GET /api/review/pending` lists titles with work. For a title, `POST
   /api/titles/{id}/review/batches` claims the oldest pending edits (`?limit=` to change the
   default of 50). If a batch is already open, you get that batch back. The response has `changes`
   (each with `kind`, `cueIds`, `suggestion`, `state` and the `command` the player sent) and
   `document`, the file plus exactly those edits.
2. Find the registered `document.json` (the repository root plus the title's document path in
   the library). Write the batch's `document` to it as the starting point, pretty-printed with
   two-space indentation like the existing file, so the Git diff shows only real changes.
3. Assess each change against neighbouring cues and available evidence, and update that file:
   translations, notes, phrase alignments and checks for every affected cue. Review both parts of
   a split together. A `review-request` asks you to check machine output: review the cue's
   translation, notes and alignment against the teaching guide, rewrite what falls short, and
   check the source text itself (see *Doubtful source text* below); one that says the model could
   not annotate the cue needs you to write its annotations. A request for a first-pass
   **translation** comes before any breakdown or notes: judge whether each translation says what
   the line means in its scene, with the right register, correct it in the file, check the names
   and source text, and write no breakdown or notes. A request for a **phrase breakdown**
   ("check chunks and links only") comes before any notes exist: judge the chunk boundaries and
   pairings against the guide's *Chunking* and *Links* sections, correct them in the file, and
   write no notes. A fault in the translation itself is still yours to fix (remove the breakdown
   and rebuild it for the new wording). A `comment` is the learner's feedback on a line (often a better translation
   or a confusing note): act on it in the file, or complete it `already_correct` if the line
   stands. A `suggestion` is a proposal: adopt it by correcting the source text, or
   keep the transcript with a specific `listeningCheck`. A `conflict` no longer applies to the file.
   Decide it by hand: edit the file to match the player's intent, or leave the file as it is and
   mark the edit `not_adopted`. Preserve IDs, timings and unrelated text. Regenerate phrase links
   that an edit cleared, against the exact source and translation.
4. Before writing, `POST /api/titles/{id}/validate` with the candidate document: it returns
   `{"problems":[...]}`, every structural problem with its cue ID, and writes nothing. Fix them
   all, then write the file and GET `/api/titles/{id}` to confirm it loads.
5. Inspect the content repository's Git diff, then commit and push to the existing upstream.
   If Git fails, leave the batch open and report it; don't regenerate saved work.
6. `POST /api/titles/{id}/review/batches/{batch}/complete` with
   `{"outcome":"applied","outcomes":{"12":"not_adopted"}}`. `outcome` covers every edit not listed;
   outcomes are `applied`, `already_correct`, `not_adopted` and `reviewed`. The server rejects
   completion while the file is unchanged since the claim, unless every edit is `not_adopted`.
7. Claim again until the response is `204`. Report retained listening checks and anything left
   open. Never edit SQLite queue rows directly.

## Doubtful source text

Before reviewing a title's lines, read its character list: `content-archive/<title>/spellings.json`
where the title was prepared here, otherwise the cast list of the source-language release (a dub
often renames characters). Check every name in the batch against it, including names misheard as
ordinary words ("Big Web" for Bigweld), and correct a misspelt name everywhere it occurs
in the batch.

Machine transcripts and comic text recognition mishear and misread. While reviewing, treat these
as doubts about the source, not as French to explain: a malformed sentence, a name spelled
differently from neighbouring cues, a word that makes no sense in the scene. For each one:

- correct the source only when the context leaves no doubt (a character's name, an obvious
  homophone), and update its translation and notes;
- otherwise keep the text and record one specific question in `listeningCheck` (video) or
  `textCheck` (comic), for example "`les chevaux` or `les cheveux`? She is brushing her hair";
- teach only what is clear in a doubtful line; delete notes that explain the doubtful words.

## Comic text blocks

The queue only shows lines with requests, so check the whole comic once its breakdown review is
done: `python3 prepare/split_block.py --document DOC --find` lists long lines holding several
sentences. A printed block (a recap, a character profile, an author's note) is one line per
sentence; split each with `--line LINE_ID`, then validate, commit and push as for a batch.
Splitting before notes are written saves moving them.

## Checks

Keep uncertainty out of teaching notes:

- Name the transcription engine and model in the commit message, not the document; do not
  repeat ASR warnings on every cue.
- `listeningCheck` holds one specific, actionable listening question; absent means no open
  question. Keep it through edits, consider both halves after a split, and
  clear it only when a user-verified correction resolves it.
- `textCheck` holds spelling or agreement doubts about the written source. A confirmed
  correction clears it.
- After a correction, remove notes about the discarded wording and update the translation.

Git is the durable content checkpoint. Until a batch is written and committed, player edits exist
only in SQLite. Keep title-specific preparation scripts and research outside live content. Don't
introduce branches, worktrees or custom Git plumbing for this workflow.
