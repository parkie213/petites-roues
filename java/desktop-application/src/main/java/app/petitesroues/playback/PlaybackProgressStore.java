package app.petitesroues.playback;

import app.petitesroues.model.progress.PlaybackProgress;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists one resume position per title in the local SQLite catalogue. */
@Repository
public class PlaybackProgressStore {
  private final JdbcTemplate jdbc;

  public PlaybackProgressStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Returns zero for a title without previously saved progress. */
  public PlaybackProgress find(String titleId) {
    return jdbc
        .query(
            "SELECT position_seconds,updated_at FROM playback_progress WHERE title_id=?",
            (row, index) ->
                new PlaybackProgress(
                    row.getDouble("position_seconds"), row.getString("updated_at")),
            titleId)
        .stream()
        .findFirst()
        .orElse(new PlaybackProgress(0, null));
  }

  /** Saved positions in seconds, keyed by title, for the library's progress bars. */
  public Map<String, Double> positions() {
    var positions = new HashMap<String, Double>();
    jdbc.query(
        "SELECT title_id,position_seconds FROM playback_progress",
        row -> {
          positions.put(row.getString("title_id"), row.getDouble("position_seconds"));
        });
    return positions;
  }

  /** Forgets a title's position; it then resumes from the start. */
  public void delete(String titleId) {
    jdbc.update("DELETE FROM playback_progress WHERE title_id=?", titleId);
  }

  public void save(String titleId, PlaybackProgress progress) {
    jdbc.update(
        """
        INSERT INTO playback_progress(title_id,position_seconds,updated_at) VALUES(?,?,?)
        ON CONFLICT(title_id) DO UPDATE SET
          position_seconds=excluded.position_seconds,updated_at=excluded.updated_at
        """,
        titleId,
        progress.positionSeconds(),
        progress.updatedAt());
  }
}
