-- Comic volume (tome) number, indexed so the library can sort and label a series' volumes
-- without opening documents. Filled by the next Add/Refresh.
ALTER TABLE titles ADD COLUMN volume INTEGER;
