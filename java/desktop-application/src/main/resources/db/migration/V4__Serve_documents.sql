-- The served copy of each study document: the accepted copy with the pending queue replayed over it,
-- as canonical JSON, stored so that a read is a lookup. It is derived state, rebuilt whenever the
-- accepted copy or the queue changes, and can be deleted at any time; a missing or stale row is
-- rebuilt on the next read.
CREATE TABLE served_documents (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  -- The accepted_documents.source_sha256 this copy was replayed over; a different hash means stale.
  accepted_sha256 TEXT NOT NULL,
  json BLOB NOT NULL
);

-- Whether the queued edit no longer applies to the served document, as decided by the last replay.
ALTER TABLE review_queue ADD COLUMN conflict INTEGER NOT NULL DEFAULT 0;
