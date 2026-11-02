-- Player edits are stored as commands and replayed over document.json until an agent writes
-- them into the file. Rows queued before this version describe edits already in the files, so
-- their command stays NULL and they are never replayed.
CREATE TABLE review_batches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  base_sha256 TEXT NOT NULL,
  created_at TEXT NOT NULL,
  completed_at TEXT
);

ALTER TABLE review_queue ADD COLUMN command TEXT;
ALTER TABLE review_queue ADD COLUMN batch_id INTEGER REFERENCES review_batches(id);
