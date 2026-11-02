CREATE TABLE IF NOT EXISTS repositories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  local_path TEXT NOT NULL UNIQUE,
  last_refreshed_at TEXT
);
CREATE TABLE IF NOT EXISTS titles (
  id TEXT PRIMARY KEY,
  repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  document_id TEXT NOT NULL,
  title TEXT NOT NULL,
  series TEXT,
  season INTEGER,
  episode INTEGER,
  source_url TEXT,
  document_path TEXT NOT NULL,
  missing INTEGER NOT NULL DEFAULT 0,
  edition TEXT,
  duration_seconds REAL,
  document_type TEXT NOT NULL DEFAULT 'video',
  UNIQUE(repository_id, document_id)
);
CREATE TABLE IF NOT EXISTS review_queue (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  timestamp TEXT NOT NULL,
  kind TEXT NOT NULL,
  cue_ids_json TEXT NOT NULL,
  suggestion TEXT,
  reviewed_at TEXT,
  outcome TEXT
);

CREATE TABLE IF NOT EXISTS playback_progress (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  position_seconds REAL NOT NULL CHECK (position_seconds >= 0),
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reading_progress (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  page_id TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
