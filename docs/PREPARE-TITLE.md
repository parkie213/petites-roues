# Prepare a title

The steps for turning a video or a comic into a study document. They are written for a person
or a coding agent to follow by hand. The library's **Add title** sets up the title folder; then
three small scripts in [prepare/](../prepare/) call hosted models through
[OpenRouter](https://openrouter.ai) and fill in the document.
There is no pipeline to maintain: when a step stops being useful, change this page.

| Step | Script | Default model |
| --- | --- | --- |
| Speech to timed source text | `prepare/transcribe.py` | `mistralai/voxtral-small-transcribe-3-0` |
| Comic page text and regions | `prepare/comic_text.py` | `qwen/qwen3.8-flash` |
| First-pass translation, notes, phrase links | `prepare/notes.py` | `openai/gpt-6-luna` |

`prepare/spellings.py` corrects misheard names from a title's spelling list; it calls no model.

Each script takes `--model` to try something else. All of them need Python 3 (standard
library only) and `OPENROUTER_API_KEY` in the environment, and print the reported cost.
`notes.py` also needs the app running, because it validates through the app and queues its
output for review (set `PETITES_ROUES_URL` if the app is not on `http://localhost:8877`).
The app and `transcribe.py` need FFmpeg, and [yt-dlp](https://github.com/yt-dlp/yt-dlp#installation)
if you download videos. Use media you have permission to use.

The order for a title is: **sample → spelling list → transcribe the rest → correct names →
first-pass notes → agent review → commit**. Always check a sample before running a whole title (see each step), so a bad model or
setting costs minutes, not a film.

## Ground rules

- **One run directory per step and attempt** under `content-archive/runs/`. The scripts save
  every request and raw response there. A run directory is a record: don't edit it, start a new one.
- **Machine output is a draft.** Notes are a first pass and go to the review queue; doubts about
  what was said or printed are recorded there as `listeningCheck` or `textCheck`.
- **The app is the validator.** `POST /api/titles/{id}/validate` checks a candidate document
  and lists every structural problem; Add/Refresh and GET reject invalid files. A rejection
  means fixing the document, not the app. Structural validity says nothing about teaching
  quality: that is the review's job.
- Keep originals: the source video/CBZ, any original subtitles and the raw run output.

## Add the title

In the library, **Add title** and pick the video or `.cbz` on this computer. Choose the
repository, the folder for the title inside it, the title and the languages:

- **Videos:** pick the audio track to keep. The track matching the source language is
  preselected; with dubs such as VFF and VFQ, check the track titles. The browser can only play
  one track, and not AC3 or DTS, so the app keeps just that track (converting it to AAC if
  needed) as `video.mp4` in the title folder. The picture is copied unchanged and the original
  file is left where it was.
- **Comics:** pick the reading direction. The CBZ is copied into the folder and the pages are
  listed in natural filename order; check the first few pages in the reader.

The app writes an empty `document.json` and indexes the title. Add metadata such as `series`,
`season`, `episode`, `sourceUrl` and `edition` to the document by hand if you want it (see
[the document format](DOCUMENT-FORMAT.md#metadata-and-review-fields)).

To download a video first: `yt-dlp -t mp4 --no-playlist -o 'video.%(ext)s' 'VIDEO_URL'`.

## Transcribe a video

```bash
python3 prepare/transcribe.py --document content/my-title/document.json \
  --run-dir content-archive/runs/my-title-transcribe --chunks 1
```

The script extracts the audio from the title's video and transcribes it in the document's source
language, in ten-minute chunks that overlap by a few seconds, adding one untranslated cue per
segment. `--chunks 1` stops after the first chunk: play it in the player and check the text and
timing. Then rerun the same command without `--chunks` to add the rest. Each chunk is saved as it
completes and rate limits are retried, so after a failure rerun the same command to resume.
Watch for hallucinated lines in silence or music (for example, broadcaster credits).

### Correct names

Transcription spells names as it hears them ("Bigwell" or "Big Web" for Bigweld), and the
OpenRouter transcription models accept no list of expected names (`keyterms` is refused, and
Voxtral ignores Mistral's `context_bias` when passed through). So after the sample, read its
transcript and write the title's spelling list, mapping each correct spelling to the variants
the model produced:

```json
{ "Bigweld": ["Bigwell", "Big Web", "Big World"], "Rodney": ["René"] }
```

Keep it as `content-archive/<title>/spellings.json`, then apply it:

```bash
python3 prepare/spellings.py --document content/my-title/document.json \
  --spellings content-archive/my-title/spellings.json --dry-run
```

Variants are replaced as whole words, case-sensitively; check the printed changes, then rerun
without `--dry-run`. Only add a variant that is never a real word in the film (a character
named René elsewhere would be renamed too). After transcribing the rest, look for new variants,
add them and rerun; cues already corrected are left alone. Cues with translations or notes are
skipped and listed, so do this before the notes first pass.

## Find a comic's text

```bash
python3 prepare/comic_text.py --document content/my-comic/document.json \
  --run-dir content-archive/runs/my-comic-text --pages 1-5
```

Pages that already have cues are skipped. Several pages go in each request (`--batch`), and
rerunning the same command resumes and retries failed batches. Expand `--pages` (for example
`--pages 6-`, or leave it out for every page) once the sample looks right. Correct boxes and
text in the reader's Annotate mode.

## List a comic's chapters

`comic_text.py` finds chapters in the same run: the model flags pages showing a chapter
heading and copies the table of contents, without changing the regions. Once the run
directory has an answer for every page, the script writes `comic.chapters`
([format](DOCUMENT-FORMAT.md#comic-chapters)) only if the headings are in order and agree with
the table of contents (same chapters, one page offset). Otherwise it prints what disagrees and
writes nothing. It never replaces existing chapters. Check two or three of the listed pages.

When it prints problems, or the volume's text was found before this existed, a coding agent
reads the pages instead:

1. Find the table of contents (usually within the first 8 pages, "Sommaire") and copy each
   chapter's number, title and printed page.
2. Work out the archive offset: the archive position of the page where the first chapter
   opens, minus its printed page.
3. Look at every predicted first page (printed page + offset) and confirm it shows that
   chapter's heading; scans can add or drop pages, so re-measure the offset where one doesn't.
4. Write the list between `pages` and `cues`, check it with `POST /api/titles/{id}/validate`,
   and commit the documents.

## Notes first pass

Run against the registered document:

```bash
python3 prepare/notes.py --document content/my-title/document.json --run-dir content-archive/runs/my-title-notes-1
```

The script fills cues that have no translation or have a stale alignment. Use `--all` to redo
everything. It sends batches of about 30 cues with neighbouring context, using
[the teaching guide](../agents/guides/translation-notes.md) as the instructions. Each cue in an
answer is checked with the app's validator and kept as soon as it passes; up to two repair
requests resend only the cues that failed. Rerunning the same command resumes.

When it finishes, the script validates the whole document, updates it in place (skipping any cue
whose text changed while it ran) and keeps a copy in the run directory. It then queues every
annotated cue as a review request, along with any cue it could not annotate and why. The player
never writes document files, so you don't need to coordinate with it: pending player edits keep
showing on top. Don't run it while an agent has a review batch open on the title. If the content
repository uses Git, commit the result.

For a new title, run it on the transcribed sample first and review that before transcribing and
annotating the rest.

## Review

The first pass is a draft. Notes can be confidently wrong, especially on idioms and
pronominal verbs, and on words the transcript misheard. The review requests from `notes.py` are
in the title's review queue: have a strong agent (Opus or Sol) work through them following
[the review workflow](../agents/workflows/review-content.md) and the title's
**Copy notes review prompt**. It rewrites weak notes and records doubtful source text as
listening or text checks. When the review keeps finding the same mistake, add it to the guide's
*Traps* section so the next first pass avoids it. Edits you make in the player are
queued and shown on top of the file until the agent writes them in
([review API](REVIEW-API.md)). Keep listening checks separate from translation review.
