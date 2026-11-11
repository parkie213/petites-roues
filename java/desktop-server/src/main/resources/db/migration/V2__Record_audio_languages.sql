-- The language of each audio stream, in track order: browsers play only a video's first audio
-- track, so the library warns when that is not the document's language.
-- Comma-separated BCP 47 primary tags ("en,fr,nl"); an untagged stream is an empty item.
-- NULL for comics.
ALTER TABLE media ADD COLUMN audio_languages TEXT;

-- Probe every video again at the next Refresh, so the column is filled in.
UPDATE media SET modified_millis = 0 WHERE kind = 'video';
