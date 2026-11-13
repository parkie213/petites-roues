-- The accepted copy of each study document: the exact bytes the application serves, imported from
-- the registered file by Add, Refresh or an explicit import. The file stays the agents' editing
-- surface; the application reads this copy.
CREATE TABLE accepted_documents (
  title_id TEXT PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,
  -- SHA-256 (hex) of json. It is the copy's revision: two copies are the same when it matches.
  source_sha256 TEXT NOT NULL,
  -- The document file's exact bytes as imported, not re-encoded.
  json BLOB NOT NULL,
  imported_at TEXT NOT NULL
);
