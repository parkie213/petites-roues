-- The local library index. It is rebuildable: documents and media are the source of truth, so a
-- catalogue from before this schema is deleted and its folders added again, not upgraded.

-- Registered folders. Each may hold study documents, media, or both.
CREATE TABLE repositories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  local_path TEXT NOT NULL UNIQUE,
  last_refreshed_at TEXT
);

-- Study documents found by Refresh, with the metadata browsing needs without opening them.
CREATE TABLE titles (
  id TEXT PRIMARY KEY,
  repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  document_id TEXT NOT NULL,
  title TEXT NOT NULL,
  series TEXT,
  season INTEGER,
  episode INTEGER,
  -- Comic volume (tome) number.
  volume INTEGER,
  -- The library shelf the document names; null means the default for its kind.
  shelf TEXT,
  -- The document's source language (BCP 47), for filtering the library by language.
  source_language TEXT,
  source_url TEXT,
  document_path TEXT NOT NULL,
  missing INTEGER NOT NULL DEFAULT 0,
  duration_seconds REAL,
  document_type TEXT NOT NULL DEFAULT 'video',
  -- The document's reference media fingerprint; links it to media with the same fingerprint.
  reference_fingerprint TEXT,
  UNIQUE(repository_id, document_id)
);
CREATE INDEX titles_reference_fingerprint ON titles(reference_fingerprint);

-- Videos and CBZ files found by Refresh, probed once per size and modification time.
CREATE TABLE media (
  repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  relative_path TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('video', 'comic')),
  size_bytes INTEGER NOT NULL,
  modified_millis INTEGER NOT NULL,
  fingerprint TEXT NOT NULL,
  duration_seconds REAL,
  -- Comma-separated ffprobe codec names of the audio streams; NULL for comics.
  audio_codecs TEXT,
  -- Whether any audio stream is browser-playable; NULL for comics.
  audio_playable INTEGER,
  -- Card images found beside the media by Refresh, relative to the folder; NULL when none.
  poster_path TEXT,
  series_poster_path TEXT,
  -- Why a poster could not be chosen (several candidates in a folder); NULL when fine.
  poster_problem TEXT,
  PRIMARY KEY (repository_id, relative_path)
);
CREATE INDEX media_fingerprint ON media(fingerprint);

-- Media the user linked to a study document by hand. Keyed by path, not by a media row: Refresh
-- replaces a folder's media rows, and the link survives it. A moved or renamed file loses its link.
-- A manual link wins over the fingerprint on both sides.
CREATE TABLE media_links (
  repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  relative_path TEXT NOT NULL,
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  linked_at TEXT NOT NULL,
  PRIMARY KEY (repository_id, relative_path)
);
CREATE INDEX media_links_title ON media_links(title_id);

-- Phones paired with this desktop through the phone listener. Only a hash of each phone's token is
-- kept; forgetting a phone deletes its row and so revokes the token.
CREATE TABLE phones (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  token_sha256 TEXT NOT NULL UNIQUE,
  paired_at TEXT NOT NULL,
  last_seen_at TEXT
);

-- An agent's claim on a title's queued player edits, against the document as it was claimed.
CREATE TABLE review_batches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  base_sha256 TEXT NOT NULL,
  created_at TEXT NOT NULL,
  completed_at TEXT
);

-- Player edits, stored as commands and replayed over the document until an agent writes them in.
CREATE TABLE review_queue (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  timestamp TEXT NOT NULL,
  kind TEXT NOT NULL,
  cue_ids_json TEXT NOT NULL,
  suggestion TEXT,
  reviewed_at TEXT,
  outcome TEXT,
  command TEXT,
  batch_id INTEGER REFERENCES review_batches(id)
);

CREATE TABLE playback_progress (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  position_seconds REAL NOT NULL CHECK (position_seconds >= 0),
  updated_at TEXT NOT NULL
);

CREATE TABLE reading_progress (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  page_id TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  -- How far through the comic the page is, (page index + 1) / page count.
  fraction REAL CHECK (fraction > 0 AND fraction <= 1)
);
