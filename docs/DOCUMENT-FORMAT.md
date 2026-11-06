# Study document format

A `document.json` describes one video or comic edition. Video documents contain timed source subtitles,
translations, learner notes and optional phrase links. It contains no
machine-specific media paths, playback progress or review queue.

The format is plain JSON, defined by the immutable Java records in
[com.petitesroues.model.document](../java/documents/src/main/java/com/petitesroues/model/document/),
with exactly one `video` or `comic` body. The unit of study is a `line`: a source text with its
translation, notes and phrase breakdown. A video cue holds one `line`; a comic box holds `lines`.
There are no `format`, `version` or `type` fields.

[StudyDocumentCodec](../java/documents/src/main/java/com/petitesroues/documents/StudyDocumentCodec.java)
reads and writes one canonical layout: two-space indentation, one array element per line,
fields in the order shown below, absent optional fields omitted and required strings,
booleans and lists always written. A file the app has written therefore round-trips unchanged,
so agents can write a review batch's `document` straight to disk. Domain validation lives in
[StudyDocumentValidator](../java/documents/src/main/java/com/petitesroues/documents/StudyDocumentValidator.java).
Document HTTP responses and `/export` use the same JSON as files. Repositories,
progress, review queue entries and edit requests remain application-owned records. React creates
a flat display view for its player components; that view is never a persisted model.
Unknown fields and legacy formats are rejected on load.

## A complete small document

This is invented example dialogue. Save it as `document.json` inside a title folder
and add its parent content folder as a repository. It can be opened without a video.

```json
{
  "id": "my-first-title",
  "title": "My first French title",
  "sourceLanguage": "fr",
  "translationLanguage": "en",
  "notesLanguage": "en",
  "edition": "Invented example",
  "video": {
    "cues": [
      {
        "start": 0.5,
        "end": 2.5,
        "line": {
          "id": "cue-001",
          "source": "Tu me manques.",
          "translation": "I miss you.",
          "notes": [
            "French makes the person missed the subject: `tu`. The person feeling the absence is `me`. English reverses these roles."
          ],
          "continuesPrevious": false
        }
      }
    ]
  }
}
```

Document IDs must be nonblank and unique within
a registered content repository. Cue IDs must be nonempty and unique within their
document (a video cue's ID is its line's `id`). Keep them stable when editing; they identify material independently of
its subtitle number or timing. UUIDs are suitable for generated cues.

`start` and `end` are finite numbers in seconds from the beginning of the reference
video. Start must be nonnegative and end must be greater than start. The reader sorts
cues chronologically. Overlap is permitted. Each cue requires a `line` object; omitted repeated fields such as `notes` default to empty lists. `translation` may be absent or null while material is
being prepared.

## Language settings

Every document requires BCP 47 tags in `sourceLanguage` (spoken language being
learned), `translationLanguage` (translation), and `notesLanguage` (explanations).
For German → English use `de`, `en`, `en`; for Korean → French use `ko`, `fr`, `fr`.
The interface currently stays in English. Translation and notes can use different
languages. One document contains one language pair; untranslated cues may still
have a null translation. No language is inferred from the field names or filenames.

## Metadata and review fields

Optional document metadata includes `series`, integer `season` and `episode`,
`sourceUrl`, `edition`, `transcriptionMethod`, `durationSeconds`.
Duration, when supplied, must be positive and finite. Use `sourceUrl` and `edition`
to identify the reference recording; the correct words with the wrong video edition
can still have unusable timings.

Describe transcription provenance once in `transcriptionMethod` (for example the ASR model).
Cue-specific fields carry more precise information:

| Cue field | Meaning |
| --- | --- |
| `continuesPrevious` | This cue continues the preceding sentence; defaults to false |
| `listeningCheck` | Optional specific unresolved question about the spoken words |
| `textCheck` | Optional written-form issue, such as spelling or agreement |
| `alignment` | Optional phrase links, described below; absent means the line needs a breakdown |

Completing an annotation review does not itself verify speech. Preserve unresolved checks
when editing, and reconsider them for both parts of a split.

Each `notes` string is one teaching point. Backticks highlight source-language expressions
inside notes and phrase explanations. They are presentation markup; do not put them
in `source`, `translation` or the phrase arrays.

## Phrase alignments

Add this optional `alignment` object to the example cue’s `line` to connect its meanings:

```json
{
  "source": [
    "Tu",
    "me",
    "manques."
  ],
  "translation": [
    "I",
    "miss",
    "you."
  ],
  "links": [
    {
      "source": [
        0
      ],
      "translation": [
        2
      ],
      "note": "`Tu` = you, the person whose absence is felt; French makes this person the subject."
    },
    {
      "source": [
        1
      ],
      "translation": [
        0
      ],
      "note": "`Me` = to me, the person feeling the absence. In `manquer à quelqu'un`, this person is an indirect object; English makes them the subject I."
    },
    {
      "source": [
        2
      ],
      "translation": [
        1
      ],
      "note": "`Manques → manquer` = be missed here, second-person singular present indicative with `tu`. `Manquer à quelqu'un` means that someone feels your absence, so `tu me manques` becomes I miss you."
    }
  ]
}
```

The phrase arrays bind the graph to the line's exact text: joining `source` with single spaces
must reproduce the line's `source` exactly, and joining `translation` its `translation`,
including punctuation and apostrophes. A graph made for older wording therefore fails
validation.

Link indices are zero-based positions in the phrase arrays. Every phrase on both
sides must be covered. A link may connect several phrases on either side, or have
an empty side when a phrase has no separate counterpart. Indices must be valid and
unique within each side of a link. A link's `note` explains it to the learner; it is
optional, because a breakdown is written and reviewed before its notes (the player shows
"No explanation yet."), and an empty note is left out of the file. Group natural meaning
units rather than mechanically splitting every word.

When the source or translation changes without a replacement graph, remove the old
`alignment`: a line without one needs a breakdown, whether it never had one or lost it to an
edit. Notes-only and timing-only changes can preserve an existing valid graph.

For the teaching standard and generation instructions, use
[translation notes](../agents/guides/translation-notes.md) and
[translation notes guide](../agents/guides/translation-notes.md).

## Files beside the document

```text
content-root/
  title-folder/
    document.json
    video.mp4
```

The video is optional. Keep at most one supported video in the title
folder: `.mp4`, `.mkv`, `.webm`, `.mov`, `.m4v`, `.avi` or `.ogv`. Extension support
does not guarantee that a browser can decode its audio/video codecs. Media must
resolve inside the title folder.

### Card images

The library's Posters view shows a card per title or series. A title card uses an optional
`poster.jpg`, `poster.jpeg`, `poster.png` or `poster.webp` beside `document.json`; without one,
a comic uses its first manifest page and a video gets a placeholder. A series card uses the
nearest `poster.*` in a folder above the title folders (for example `peppa-pig/poster.jpg`),
never the repository root; without one it uses its first volume's or episode's card. Keep at
most one poster per folder. Posters are checked when the library is browsed, so no Refresh is
needed. Card-sized copies are cached locally in `content/.cache/cards/`
(`petitesroues.cards.cache`) and can be deleted at any time.

Posters group titles from document metadata, never folder names: comics by `series` and
`comic.volume`, videos with a season or episode by `series`, and other videos as films. Series
names match ignoring case and spacing, across repositories; copies of the same volume or episode
share one card.

Add/Refresh indexes documents and folder hierarchy. Refresh after adding/moving
documents or changing indexed title metadata; reopening the player reloads document
contents and checks for sibling media. Removing a repository unregisters local
state without deleting its files.

Copying a title folder transfers its material. Video stays local unless you copy it;
JSON export alone does not include video. A separate content Git
repository can checkpoint documents while ignoring media. Local
SQLite state is configured independently through `spring.datasource.url`, defaulting
to `jdbc:sqlite:content/library.sqlite`.

## Comic documents

The shared `StudyDocument` owns identity, title, series, edition, source URL and language roles.
`VideoDocument` owns season/episode, duration, transcription provenance and timed cues.
`ComicDocument` owns an ordered page manifest and reading direction; it has no timestamps,
audio checks or video cues. The selected body determines the media type.

```json
{
  "id": "example-comic",
  "title": "Example comic",
  "sourceLanguage": "fr",
  "translationLanguage": "en",
  "notesLanguage": "en",
  "comic": {
    "volume": 1,
    "readingDirection": "rtl",
    "pages": [
      {
        "id": "cover",
        "entry": "001.jpg"
      },
      {
        "id": "page-one",
        "entry": "002.jpg",
        "label": "1"
      }
    ],
    "chapters": [
      {
        "number": 1,
        "title": "Cruauté",
        "pageId": "page-one"
      }
    ],
    "boxes": []
  }
}
```

Place one `.cbz` beside `document.json`. The archive stays local, like video. Add/Refresh
indexes the comic type and metadata; normal library browsing does not decode documents or
scan the archive. Opening the reader checks all manifest entries against the CBZ. Pages are
served directly from the archive without extracting files.

`comic.volume` is the optional volume (tome) number within `series`, a positive integer. The
library sorts and labels a series' volumes by it ("Tome 3"); keep the full name in `title`
("Demon Slayer — Tome 3") for the reader header. Add/Refresh indexes it.

Manifest order is reading order, independent of ZIP entry order and filename sorting.
`comic.readingDirection` is `ltr` (the default) or `rtl`; it controls the arrow keys, not manifest order.
Page IDs must be unique and use letters, numbers, underscores or hyphens. Keep them stable
when replacing images or reordering pages so saved positions remain meaningful. Entries
must be distinct, exact relative archive names ending in `.jpg`, `.jpeg`, `.png` or `.webp`;
absolute paths, backslashes and empty/dot/parent path components are rejected. A label is
optional printed page numbering, separate from archive position. Documents require 1–10000
pages. Duplicate archive entries and archives with over 20000 entries are rejected; each
served image is limited to 32 MiB uncompressed.

`/reader/{local-title-id}/` provides page navigation, page selection, fit-page/fit-width
and zoom. Left/right keys follow reading direction; Home/End select the first/last page.
Successfully displayed pages save `pageId` in SQLite `reading_progress`, separately from
video `playback_progress`, with the page's `(index + 1) / page count` as `fraction` for the
library's progress bars. Refresh preserves it, clearing it from a library card or unregistering
the repository removes it.
Missing saved page IDs fall back to the first page. Progress is never included in JSON exports.

### Comic chapters

The optional `comic.chapters` list (default empty) names the volume's chapters in reading
order. Each starts on `pageId` and runs until the next chapter's first page or the end of the
volume; pages before the first chapter (cover, contents) belong to none. `title` is the
printed title in the source language, without the "Chapitre N" prefix; `number` is the
optional positive printed chapter number, absent for extras and bonus sections. Chapter page
IDs must exist and appear in strictly increasing manifest order. The readers show the current
chapter and jump to a chapter's first page. `prepare/comic_text.py` fills the list when it
finds the text, or an agent reads it from the volume's table of contents
([Prepare a title](PREPARE-TITLE.md#list-a-comics-chapters)).

### Comic boxes

A `Line` holds shared text, translation, notes, phrase alignments, continuation and written
checks. A `VideoCue` is one subtitle: its timing and audio verification beside one `line`.
A `ComicBox` is a box on a page: its own `id`, its placement, and `lines`, the one or more
lines printed in it in reading order.

A comic's optional `comic.boxes` list defaults to empty. Each box has an `id` (a UUID) that
identifies the box itself and never changes with its text, a stable page ID, a positive `order`
unique on that page, one or more rectangles in normalized coordinates relative to the entire
original image (including margins), and at least one line. Box IDs are unique among boxes and
line IDs unique across the comic; the two are separate namespaces. Explicit order determines the bubble sequence; position
alone does not determine manga reading order. Coordinates must be finite, dimensions positive,
and every rectangle must fit within [0, 1] on both axes.

```json
{
  "id": "6f1c2a9e-4b7d-4e0a-9c3f-2d8b5e7a1c40",
  "pageId": "page-one",
  "order": 1,
  "regions": [
    {
      "x": 0.6,
      "y": 0.1,
      "width": 0.2,
      "height": 0.15
    }
  ],
  "lines": [
    {
      "id": "bubble-1",
      "source": "Bonjour !",
      "translation": "Hello!",
      "notes": [
        "A greeting."
      ]
    }
  ]
}
```

A speech bubble usually has one line. A printed text block too long for one card (a recap
paragraph, a character profile) is split into one line per sentence, each with its own ID,
translation, notes, alignment and checks; readers show a box with several lines a sentence at
a time or as a list. Text edits, comments and review requests address a line by its ID; moving
or deleting a box addresses the box's ID and takes all its lines along.
`prepare/split_block.py` splits an aligned line into its sentences at sentence-ending chunks.

`annotationMethod` records extraction provenance on the comic.
Drawn boxes start with one line whose source may be empty while awaiting OCR or transcription,
so review/OCR can target it before any text is available.
Use `textCheck` for a specific unresolved issue. Comic boxes have no listening checks or timestamps.

The reader overlays numbered, keyboard-focusable regions. Selecting a region or using the bubble
selector opens its text, translation and learner notes. Geometry scales with the displayed image
in fit-page, fit-width and custom zoom modes. Highlights and the study panel can be hidden.

`PUT /api/titles/{id}/comic/lines/{lineId}` accepts `expectedSource` and `source`. Stale source edits
are rejected. Changed text resets verification, clears translation/notes/alignment and queues the
edit. IDs, the box and the other lines are preserved.
Unchanged text queues nothing. The editor blocks page/bubble changes until Save or Cancel.
Like every player edit, it is served over the file until an agent writes it there through the
[review API](REVIEW-API.md), with the annotations.

In Annotate mode, dragging on the page immediately saves a box through
`POST /api/titles/{id}/comic/boxes` with `pageId`, `region` and optional `source`; the server
assigns UUIDs to the box and its first line. `PUT …/comic/boxes/{boxId}/placement` moves or
resizes a box and `DELETE …/comic/boxes/{boxId}` removes it with its lines.
Creating, moving/resizing or deleting a box queues an `annotate` edit in the
same review queue as video edits. Text can be added afterward through the existing
comic text editor. Failed box saves remain available for retry or discard.
