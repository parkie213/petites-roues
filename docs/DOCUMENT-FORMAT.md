# Study document format

A study document is a JSON file, of any name, that describes one video or comic. Video documents contain timed source subtitles,
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

This is invented example dialogue. Save it as a `.json` file (for example `document.json`) in a
folder and add that folder to the library. It can be opened without a video.

```json
{
  "id": "my-first-title",
  "title": "My first French title",
  "sourceLanguage": "fr",
  "translationLanguage": "en",
  "notesLanguage": "en",
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

Optional document metadata includes `series`, `shelf`, `sourceUrl`, and on a video integer
`season` and `episode`. `shelf` names the library shelf the title is shown on, any label you like
("Manga", "BD", "YouTube"); without it a comic goes on Comics, a video with a season or episode on
Series, and other videos on Films. Media without a study document always uses those defaults. How the material was prepared (which ASR or vision model, which pass) is not part of
the document: it belongs in the commit message, with raw model output kept in `content-archive/`.
Cue-specific fields carry review information:

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

The phrase arrays bind the graph to the line's exact text: joining `source` must reproduce the
line's `source` exactly, and joining `translation` its `translation`, including punctuation and
apostrophes. Chunks join with a single space, except in languages written without spaces
between words (Chinese, Japanese, Thai, Lao, Khmer and Burmese, by the document's language
tag), whose chunks join with nothing: `我们` `去` `公园。` reproduces `我们去公园。`. A graph made for older wording therefore fails
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

## Documents and media

The library reads registered folders. A folder may hold study documents, media, or both, and
the media can live anywhere: a Plex library, a NAS, a drive or beside the documents.

```text
media/
  peppa-pig/
    Les Flaques de Boue.mp4
documents/
  peppa-pig/
    s01e01/document.json      its reference fingerprint matches the video above
```

- **Study documents.** Refresh reads every `.json` file whose top level has `sourceLanguage` and
  a `video` or `comic` body, whatever its name. Other JSON is ignored. A file that claims to be a
  study document but doesn't validate fails the Refresh with its path.
- **Media.** Refresh indexes every video (`.mp4`, `.mkv`, `.webm`, `.mov`, `.m4v`, `.avi`,
  `.ogv`) and `.cbz`, with its [fingerprint](#reference-media), duration and audio codecs. A file
  is probed again only when its size or modification time changes. Hidden folders,
  `node_modules` and `content-archive` are skipped.
- **Linking.** A video or CBZ links to a document whose `reference.fingerprint` equals its
  fingerprint, in any registered folder. Nothing else links them automatically: not sharing a
  folder, nor series, episode or duration. A document without a reference fingerprint has no
  media until one is recorded (`prepare/new_title.py` or `prepare/reference.py`) or a file is
  linked by hand. When several copies match, one in the document's own folder is preferred.
- **Linking by hand.** When fingerprints don't match (the document was timed against someone
  else's copy), choose **Link to study document…** on the media's card, or **Locate media…** on
  the document in the Documents view. The link is kept in the library's SQLite, keyed by the
  media's folder and path; the document is not changed, so another library does not see it
  (record the file as the reference with `prepare/reference.py` to share the fix). A hand link
  wins over fingerprints on both sides, survives Refresh, and is lost when the file is moved or
  renamed. A hand-linked video whose duration differs from `reference.durationSeconds` by more
  than 2 seconds warns that cue timings may be off.
- **Warnings.** Media that no document references is listed under *Needs attention* as "No study
  document". A video with no browser-playable audio track (AAC, MP3, Opus, Vorbis or FLAC) warns
  "No audio track this player can play"; remux it with your own tools. Refresh clears a warning
  once it is fixed.

### Card images

The library's Posters view shows a card per title or series, for media with or without a study
document. A study document whose media was not found has no card; the Documents view lists every
document with the media it links to, or why it has none. When the library holds documents in more than
one `sourceLanguage`, chips above Posters show one language at a time (Documents always lists every document); media without
a study document has no declared language and shows only under All languages. Posters live with the media, as in a Plex library, not with study documents, so they
show before any document exists. Images are `.jpg`, `.jpeg`, `.png` or `.webp`:

- A video or CBZ alone in its folder (a film or tome folder) uses that folder's `poster.*`.
- Any video or CBZ can have an image with its own file name, such as `ponyo.jpg` beside
  `ponyo.mkv`; it wins over `poster.*`.
- A series card uses `poster.*` in a folder shared by several episodes or volumes (for example
  `peppa-pig/poster.jpg` beside its videos), or the nearest one above a title folder, up to and
  including the registered folder. A film without its own poster uses that card too.

Without an image, a comic uses its first page and a video gets a placeholder. Keep at most one
poster per folder: when a folder has several, the media it affects shows a (!) and no poster. Refresh finds posters, so click **Refresh library** after adding one; replacing an existing poster's image needs no Refresh. Card-sized copies are cached locally in `content/.cache/cards/`
(`petitesroues.cards.cache`) and can be deleted at any time.

Posters group titles from document metadata onto shelves (see `shelf` above), ordered by name:
comics by `series` and `comic.volume`, videos with a season or episode by `series`, and other
videos one card each. A series card sits on the shelf most of its titles name, so one volume
without a `shelf` doesn't split its series. Media without a document is named from
its path instead, as media servers do: the series is its nearest folder that is not a `Season N`
or episode folder, and `s01e02` or a leading number (`083-bienenalarm.mp4`) gives the episode.
Those names are for display only; linking is by fingerprint alone. Series names match ignoring
case and spacing, across folders, so a video awaiting its document sits in its series beside the
episodes that have one. Copies of the same volume or episode share one card.

Add/Refresh indexes documents, media and folder hierarchy. Refresh after adding, moving or
changing documents or media; reopening the player reloads document contents. Removing a folder
unregisters local state without deleting its files.

Copying a document transfers its material. Video stays local unless you copy it;
JSON export alone does not include video. A separate content Git
repository can checkpoint documents while ignoring media. Local
SQLite state is configured independently through `spring.datasource.url`, defaulting
to `jdbc:sqlite:content/library.sqlite`.

## Reference media

Each body may record its `reference`: the copy of the media its cues were timed or its boxes were
drawn against. The correct words with the wrong cut of a video can still have unusable timings,
so the reference says which copy the material fits. It describes the material, not where a copy
is on this machine, and every field is optional.

```json
"video": {
  "season": 1,
  "episode": 3,
  "reference": {
    "fingerprint": "v1:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    "sizeBytes": 734003200,
    "durationSeconds": 1312.48
  },
  "cues": []
}
```

| Field | Meaning |
| --- | --- |
| `fingerprint` | `v1:` and 64 lowercase hex digits, defined below; says whether a local file is this exact copy |
| `sizeBytes` | The file's size in bytes, positive |
| `durationSeconds` | Video only: the reference duration in seconds, positive and finite; a different cut or speed differs |

The v1 fingerprint is the SHA-256 of the file size as 8 bytes big-endian, then the first 4 MiB,
then the last 4 MiB; a file of at most 8 MiB is hashed once, whole, after its size. Reading only
the ends keeps a large video fast to check, and still tells re-encodes apart: MP4 sample tables
sit at one end, and a CBZ's central directory, with every entry's CRC, sits at its end.

A shared content repository rarely matches by fingerprint, because each person's download or rip
is a different file; the fingerprint recognises the author's own copy, and the duration is what
tells a different cut apart. `prepare/new_title.py` records the reference when it starts a
document; `prepare/reference.py` records it for an existing one.

## Comic documents

The shared `StudyDocument` owns identity, title, series, source URL and language roles.
`VideoDocument` owns season/episode, its reference video and timed cues.
`ComicDocument` owns its reference CBZ, an ordered page manifest and reading direction; it has no timestamps,
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
        "entry": "001.jpg",
        "boxes": []
      },
      {
        "id": "page-one",
        "entry": "002.jpg",
        "label": "1",
        "boxes": []
      }
    ],
    "chapters": [
      {
        "number": 1,
        "title": "Cruauté",
        "pageId": "page-one"
      }
    ]
  }
}
```

The `.cbz` links to the document by fingerprint, like a video, and stays local. Add/Refresh
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

Each page's `boxes` list (default empty) holds the boxes printed on it, in reading order: a
box's page is the page that holds it, and its position in the list is its reading order. The
list order is explicit because position on the page alone does not determine manga reading
order. Each box has an `id` (a UUID) that identifies the box itself and never changes with its
text, one or more rectangles in normalized coordinates relative to the entire original image
(including margins), and at least one line. Box IDs and line IDs are each unique across the
whole comic, and the two are separate namespaces. Coordinates must be finite, dimensions
positive, and every rectangle must fit within [0, 1] on both axes. Chapters stay a separate
list that names each chapter's first page, rather than holding pages.

```json
{
  "id": "6f1c2a9e-4b7d-4e0a-9c3f-2d8b5e7a1c40",
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
assigns UUIDs to the box and its first line and adds it after the page's last box.
`PUT …/comic/boxes/{boxId}/placement` moves or resizes a box, and with an optional `order`
moves it to that one-based position on its page. `DELETE …/comic/boxes/{boxId}` removes it with
its lines.
Creating, moving/resizing or deleting a box queues an `annotate` edit in the
same review queue as video edits. Text can be added afterward through the existing
comic text editor. Failed box saves remain available for retry or discard.
