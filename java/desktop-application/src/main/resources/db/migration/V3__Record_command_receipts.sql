-- Receipts survive restart so retrying an add/split cannot assign another identity.
CREATE TABLE command_receipts (
  title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
  request_id TEXT NOT NULL,
  request_json TEXT NOT NULL,
  result_json TEXT NOT NULL,
  PRIMARY KEY (title_id, request_id)
);
