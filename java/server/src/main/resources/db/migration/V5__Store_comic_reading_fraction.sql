-- How far through its comic a saved page is, (page index + 1) / page count, so library cards can
-- show reading progress without opening documents. Earlier rows stay NULL until the next save.
ALTER TABLE reading_progress ADD COLUMN fraction REAL CHECK (fraction > 0 AND fraction <= 1);
