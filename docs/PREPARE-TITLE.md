# Prepare a title

The steps for turning a video or a comic into a study document. They are written for a person
or a coding agent to follow by hand. `prepare/new_title.py` starts the document; then
small scripts in [prepare/](../prepare/) call hosted models through
[OpenRouter](https://openrouter.ai) and fill in the document.
There is no pipeline to maintain: when a step stops being useful, change this page.

**What goes to OpenRouter, and what doesn't.** OpenRouter does the bulk first passes, with
non-Claude models only:

- the video's first audio track, as ten-minute MP3 chunks (`transcribe.py`);
- comic page images, a few pages per request (`comic_text.py`);
- batches of about 30 lines with their neighbours and [the teaching guide](../agents/guides/translation-notes.md),
  for the first-pass translations, breakdowns and notes (`translate.py`, `phrases.py`, `notes.py`):
  the source text, plus the reviewed translations and breakdowns the later stages build on.

Everything else stays in the coding agent's session: checking the media and audio language,
writing the character list and correcting names, every review of the three annotation stages,
listening and text checks, commits, and the guide's *Traps*. Never route Claude through
OpenRouter, and don't send reviews, whole documents or anything else there.

| Step | Script | Default model |
| --- | --- | --- |
| Speech to timed source text | `prepare/transcribe.py` | `mistralai/voxtral-small-transcribe-3-0` |
| Comic page text and regions | `prepare/comic_text.py` | `openai/gpt-6.1-terra` |
| First-pass translation | `prepare/translate.py` | `openai/gpt-6-luna` |
| First-pass phrase breakdowns (chunks and links) | `prepare/phrases.py` | `openai/gpt-6-luna` |
| First-pass link and sentence notes | `prepare/notes.py` | `openai/gpt-6-luna` |

`prepare/spellings.py` finds and corrects misheard names from a title's character list; it calls no model.

Each script takes `--model` to try something else. All of them need Python 3 (standard
library only) and `OPENROUTER_API_KEY` in the environment, and print the reported cost.
`translate.py`, `phrases.py` and `notes.py` also need the app running, because they validate
through the app and queue their output for review (set `PETITES_ROUES_URL` if the app is not
on `http://localhost:8877`).
The app and `transcribe.py` need FFmpeg, and [yt-dlp](https://github.com/yt-dlp/yt-dlp#installation)
if you download videos. Use media you have permission to use.

The order for a title is: **check the audio language → character list → sample → correct
names → transcribe the rest → correct names → translate → translation review → break down →
breakdown review → notes → notes review**. Always check a sample before running a whole title
(see each step), so a bad model or setting costs minutes, not a film.

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

## Start the document

Put the video or `.cbz` in your media folder, then start its document in your documents folder:

```bash
python3 prepare/new_title.py --document ~/gits/study-content/comics/naruto/t02/document.json \
  --media ~/media/fr/comics/naruto/Naruto-T02.cbz --title "Naruto T02" --series Naruto --volume 2
```

The script writes an empty `document.json` with the media recorded as its
[reference](DOCUMENT-FORMAT.md#reference-media); the media stays where it is. The document ID
defaults to the title with dashes (`naruto-t02`) and must be unique in the documents folder.
Languages default to French source with English translation and notes (`--source`,
`--translation`, `--notes`).

- **Comics:** the pages are listed in natural filename order and read right to left unless
  `--ltr`; check the first few pages in the reader.
- **Videos:** browsers play only a file's first audio track, whatever track is marked default,
  and not AC3 or DTS, and `transcribe.py` transcribes the same track. `new_title.py` and
  `transcribe.py` refuse a video whose first track is tagged with another language than the
  `sourceLanguage`, listing its tracks; an untagged track is only reported, so listen to the
  start before transcribing. Pass `--ignore-track-language` when a tag is wrong. The library also
  names the language that plays when a file has several, and warns when it isn't the document's
  `sourceLanguage`. Reorder the tracks so the one you study comes
  first, keeping the others (`ffprobe -v error -show_entries stream=index:stream_tags=language -select_streams a in.mkv`
  lists them; `0:a:1` is the second):
  `ffmpeg -i in.mkv -map 0:v -map 0:a:1 -map 0:a:0 -map 0:a:2 -map 0:s? -c copy -disposition:a 0 -disposition:a:0 default out.mkv`.
  If that track is AC3 or DTS, convert it to AAC instead of copying it (`-c:a:0 aac -b:a:0 192k`).
  A different file has a different fingerprint, so start the document from the remuxed file, or
  record it with `prepare/reference.py`.

Then **Refresh** the documents folder in the library. Add other metadata such as `season`,
`episode` and `sourceUrl` by hand if you want it (see
[the document format](DOCUMENT-FORMAT.md#metadata-and-review-fields)). For a document that
already exists, record its media with `prepare/reference.py --document path/to/document.json --media FILE`.

To download a video first: `yt-dlp -t mp4 --no-playlist -o 'video.%(ext)s' 'VIDEO_URL'`.

## Transcribe a video

```bash
python3 prepare/transcribe.py --document content/my-title/document.json \
  --media ~/media/fr/films/my-title/my-title.mp4 \
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
Voxtral ignores Mistral's `context_bias` when passed through). So **before transcribing**, write
the title's character list: every character, place and invented name, spelled as the source
language's release spells them (its cast list, credits, official site or wiki; a dub often renames
characters, so Hiccup is Harold in French). Keep it as `content-archive/<title>/spellings.json`,
each name with no variants yet:

```json
{ "Bigweld": [], "Rodney": [], "Fender": [] }
```

After the sample, list what the transcript has that looks like a listed name:

```bash
python3 prepare/spellings.py --document content/my-title/document.json \
  --spellings content-archive/my-title/spellings.json --suggest
```

It prints each look-alike word or word pair with the cues it is in (`Bigweld ← 'Big Web' ×3`)
and changes nothing. Real words turn up too ("stock" for Stoïck), so open the cue before adding
a variant. Add the misheard ones under their name:

```json
{ "Bigweld": ["Bigwell", "Big Web", "Big World"], "Rodney": ["René"], "Fender": [] }
```

then apply the list:

```bash
python3 prepare/spellings.py --document content/my-title/document.json \
  --spellings content-archive/my-title/spellings.json --dry-run
```

Variants are replaced as whole words, case-sensitively; check the printed changes, then rerun
without `--dry-run`. Only add a variant that is never a real word in the film (a character
named René elsewhere would be renamed too). After transcribing the rest, run `--suggest` again,
add the new variants and rerun; cues already corrected are left alone. Cues with translations or notes are
skipped and listed, so do this before the notes first pass.

## Find a comic's text

```bash
python3 prepare/comic_text.py --document content/my-comic/document.json \
  --media ~/media/fr/comics/my-comic/my-comic-T01.cbz \
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
4. Write the list after `pages`, check it with `POST /api/titles/{id}/validate`,
   and commit the documents.

## Annotate in three stages

A cue's teaching material is built in three stages. In each, a script has the default model
(`openai/gpt-6-luna`) write a first pass, and then the coding agent running these steps reviews
it itself, in the same session, before starting the next script:

1. **Translate** (`translate.py`), then the agent reviews the translations.
2. **Break down** (`phrases.py`): the line divided into chunks linked to the settled translation,
   with no notes yet. The agent reviews the chunks and links.
3. **Notes** (`notes.py`): a note for every link and the sentence notes, written for the
   reviewed chunks. The agent reviews the notes.

Each review settles what the next stage builds on: a translation changed after its breakdown
loses the breakdown, and a chunk changed after its notes means rewriting them. Keeping the stages
apart lets each request do one job well. Each stage leaves a valid document, so the player shows
what exists so far. How to review is under [Review](#review).

All three scripts work the same way. They send batches of about 30 cues with neighbouring
context, using [the teaching guide](../agents/guides/translation-notes.md) as the instructions.
Each cue in an answer is checked with the app's validator and kept as soon as it passes; up to
two repair requests resend only the cues that failed. Rerunning the same command resumes. When
it finishes, a script validates the whole document, updates it in place (skipping any cue that
changed while it ran), keeps a copy in the run directory and queues every cue it wrote as a review
request, with any cue it could not do and why. The player never writes document files, so you
don't need to coordinate with it: pending player edits keep showing on top. Don't run a script
while an agent has a review batch open on the title. `--no-write` keeps the answers in the run
directory only, and `--cues FILE` (a list of cue IDs) limits a run to a sample.

For a new title, take the transcribed sample through all three stages and their reviews before
transcribing and annotating the rest.

### Translate

```bash
python3 prepare/translate.py --document content/my-title/document.json \
  --run-dir content-archive/runs/my-title-translate-1
```

Fills cues that have no translation; `--all` redoes every cue. A retranslated cue loses its
phrase breakdown, which was bound to the old translation.

Then review the translations (see [Review](#review)) before breaking them down.

### Break down

```bash
python3 prepare/phrases.py --document content/my-title/document.json \
  --run-dir content-archive/runs/my-title-phrases-1
```

With each translation fixed, the model divides the line into chunks and links them to the
English, following the guide's *Chunking* and *Links* sections. By default it handles translated
cues with no breakdown; `--all` redoes every translated cue. A chunk of seven or
more words is sent back once, asking for a split at a clause or adverbial boundary, or for it to
stay whole only when the English wraps around it. A long chunk that survives is named in that
cue's review request.

Then review the breakdowns (see [Review](#review)) before writing notes: changing a chunk later
means rewriting its notes.

To compare models or prompt changes, run a few dozen cues without writing, then compare:

```bash
python3 prepare/phrases.py --document DOC --run-dir content-archive/runs/my-title-phrases-try-a \
  --cues content-archive/my-title/phrase-sample.txt --no-write --model MODEL
python3 prepare/phrase_report.py --document DOC --out phrase-report.md \
  content-archive/runs/my-title-phrases-try-a content-archive/runs/my-title-phrases-try-b
```

The report prints chunk sizes for the current breakdowns and each run, and writes every cue's
breakdowns side by side.

### Split comic text blocks

A printed text block (a recap, a character profile, an author's note) is one line per sentence
([comic boxes](DOCUMENT-FORMAT.md#comic-boxes)), but text finding returns it as one line.
Once its breakdown is reviewed and before notes, list the candidates and split each block:

```bash
python3 prepare/split_block.py --document content/my-comic/document.json --find
python3 prepare/split_block.py --document content/my-comic/document.json --line LINE_ID
```

`--find` lists lines of 150 or more characters (`--min-length`) holding several sentences.
Split the printed blocks; a speech balloon of a few short sentences can stay whole.

### Notes

```bash
python3 prepare/notes.py --document content/my-title/document.json \
  --run-dir content-archive/runs/my-title-notes-1
```

Handles cues with a current breakdown in which some link has no note; `--all` redoes every cue
with a breakdown. The chunks and links are fixed: the model writes one note per link and the
sentence notes. Then review the notes (see [Review](#review)).

## Review

Every first pass is a draft. Notes can be confidently wrong, especially on idioms and
pronominal verbs, and on words the transcript misheard. Each script queues a review request for
every cue it wrote, in the title's review queue. The coding agent running these steps reviews
them itself, in the same session, once each script finishes: it claims the batches, writes its
corrections into the document, commits and completes them as
[the review workflow](../agents/workflows/review-content.md) describes. Use a strong model for
the session (Opus or Sol), read [the teaching guide](../agents/guides/translation-notes.md)
first, and keep the title's character list (`content-archive/<title>/spellings.json`) to hand.
Each review checks only what its stage produced:

- **Translation review:** does each translation say what the line means in this scene, with its
  register and tone? Idioms, pronominal verbs and familiar words used in a less familiar sense
  are the usual misses. Names must match the character list. A line whose source looks misheard
  gets a `listeningCheck` (or `textCheck`) now, before anything is built on it.
- **Breakdown review:** chunk boundaries and links only, against the guide's *Chunking* and
  *Links* sections. No notes are written yet; a translation still at fault is retranslated and
  its breakdown rebuilt.
- **Notes review:** every link note and sentence note against the guide: why the French means
  that English here, not just a gloss. Rewrite weak notes, delete notes that explain doubtful
  words, and explain each character's name pun at its first appearance in the title.

Review a new title's sample through all three stages before running the rest, so systematic
mistakes are fixed in the guide, not cue by cue. When a review keeps finding the same mistake,
add it to the guide's *Traps* section so the next first pass avoids it. Edits you make in the
player are queued and shown on top of the file until the agent writes them in
([review API](REVIEW-API.md)). Keep listening checks separate from translation review.
