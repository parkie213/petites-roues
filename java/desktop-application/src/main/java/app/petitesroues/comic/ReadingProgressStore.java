package app.petitesroues.comic;

import app.petitesroues.model.progress.ReadingProgress;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists one page identity per local title; repository removal cascades through its foreign key.
 */
@Repository
public class ReadingProgressStore {
  private final JdbcTemplate jdbc;

  public ReadingProgressStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Returns an unsaved position when the title has never been read. */
  public ReadingProgress find(String titleId) {
    return jdbc
        .query(
            "SELECT page_id,updated_at FROM reading_progress WHERE title_id=?",
            (row, index) ->
                new ReadingProgress(row.getString("page_id"), row.getString("updated_at")),
            titleId)
        .stream()
        .findFirst()
        .orElse(new ReadingProgress(null, null));
  }

  /**
   * Replaces this local title's position and how far through the comic it is, (page index + 1) /
   * page count; validation and transactions belong to the service.
   */
  public void save(String titleId, ReadingProgress progress, double fraction) {
    jdbc.update(
        """
        INSERT INTO reading_progress(title_id,page_id,updated_at,fraction) VALUES(?,?,?,?)
        ON CONFLICT(title_id) DO UPDATE SET
          page_id=excluded.page_id,updated_at=excluded.updated_at,fraction=excluded.fraction
        """,
        titleId,
        progress.pageId(),
        progress.updatedAt(),
        fraction);
  }

  /**
   * Saved reading fractions keyed by title, for the library's progress bars. Positions saved before
   * fractions were stored are left out until the comic is next read.
   */
  public Map<String, Double> fractions() {
    var fractions = new HashMap<String, Double>();
    jdbc.query(
        "SELECT title_id,fraction FROM reading_progress WHERE fraction IS NOT NULL",
        row -> {
          fractions.put(row.getString("title_id"), row.getDouble("fraction"));
        });
    return fractions;
  }

  /** Forgets a title's page; it then opens at the first page. */
  public void delete(String titleId) {
    jdbc.update("DELETE FROM reading_progress WHERE title_id=?", titleId);
  }
}
