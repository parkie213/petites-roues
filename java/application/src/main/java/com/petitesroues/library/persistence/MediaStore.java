package com.petitesroues.library.persistence;

import com.petitesroues.model.library.IndexedMedia;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The media found in registered folders at their last refresh. Owns SQL and row mapping; services
 * own probing, reconciliation and transactions. No filesystem access occurs here.
 */
@Repository
public class MediaStore {
  private static final String SELECT =
      "SELECT m.*,r.local_path AS repository_path FROM media m JOIN repositories r ON m.repository_id=r.id";
  private final JdbcTemplate jdbc;

  public MediaStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static IndexedMedia map(ResultSet row) throws SQLException {
    Object playable = row.getObject("audio_playable");
    return new IndexedMedia(
        row.getString("repository_id"),
        row.getString("repository_path"),
        row.getString("relative_path"),
        row.getString("kind"),
        row.getLong("size_bytes"),
        row.getLong("modified_millis"),
        row.getString("fingerprint"),
        (Double) row.getObject("duration_seconds"),
        row.getString("audio_codecs"),
        playable == null ? null : ((Number) playable).intValue() != 0);
  }

  /** Every indexed media file, ordered by folder and path. */
  public List<IndexedMedia> all() {
    return jdbc.query(SELECT + " ORDER BY m.repository_id, m.relative_path", (row, i) -> map(row));
  }

  /** The media indexed for one folder, ordered by path. */
  public List<IndexedMedia> inRepository(String repositoryId) {
    return jdbc.query(
        SELECT + " WHERE m.repository_id=? ORDER BY m.relative_path",
        (row, i) -> map(row),
        repositoryId);
  }

  /** Media with this fingerprint in any folder, ordered by folder and path. */
  public List<IndexedMedia> withFingerprint(String fingerprint) {
    return jdbc.query(
        SELECT + " WHERE m.fingerprint=? ORDER BY m.repository_id, m.relative_path",
        (row, i) -> map(row),
        fingerprint);
  }

  /**
   * Replaces a folder's media index with a complete refresh result, inside the caller's
   * transaction.
   */
  public void replace(String repositoryId, List<IndexedMedia> media) {
    jdbc.update("DELETE FROM media WHERE repository_id=?", repositoryId);
    for (var item : media)
      jdbc.update(
          """
          INSERT INTO media (repository_id, relative_path, kind, size_bytes, modified_millis,
            fingerprint, duration_seconds, audio_codecs, audio_playable)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          repositoryId,
          item.relativePath(),
          item.kind(),
          item.sizeBytes(),
          item.modifiedMillis(),
          item.fingerprint(),
          item.durationSeconds(),
          item.audioCodecs(),
          item.audioPlayable() == null ? null : item.audioPlayable() ? 1 : 0);
  }
}
