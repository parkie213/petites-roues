package com.petitesroues.library.persistence;

import com.petitesroues.model.library.ContentRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes registered repositories and their title counts in SQLite. Owns SQL and row
 * mapping; the service owns ownership rules, workflow ordering and transaction boundaries. No
 * filesystem access or document decoding occurs here.
 */
@Repository
public class ContentRepositoryStore {
  private final JdbcTemplate jdbc;

  public ContentRepositoryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Lists registered repositories and indexed title/missing counts without accessing their files.
   */
  public List<ContentRepository> list() {
    return jdbc.query(
        """
      SELECT r.*,count(t.id) AS titles,coalesce(sum(t.missing),0) AS missing,
        (SELECT count(*) FROM media m WHERE m.repository_id=r.id) AS media
      FROM repositories r LEFT JOIN titles t ON r.id=t.repository_id
      GROUP BY r.id ORDER BY r.name
      """,
        (row, i) ->
            new ContentRepository(
                row.getString("id"),
                row.getString("name"),
                row.getString("local_path"),
                row.getString("last_refreshed_at"),
                row.getInt("titles"),
                row.getInt("missing"),
                row.getInt("media")));
  }

  /**
   * Returns a registered repository with its index counts.
   *
   * @throws NoSuchElementException if the repository ID is unknown
   */
  public ContentRepository find(String id) {
    return list().stream()
        .filter(r -> r.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown repository"));
  }

  /** Registers an already-resolved local folder; the service supplies its identity and name. */
  public void insert(String id, String name, String localPath) {
    jdbc.update("INSERT INTO repositories(id,name,local_path) VALUES(?,?,?)", id, name, localPath);
  }

  /** Starts reconciliation; the service subsequently marks discovered titles present. */
  public void markTitlesMissing(String repositoryId) {
    jdbc.update("UPDATE titles SET missing=1 WHERE repository_id=?", repositoryId);
  }

  /** Records the successful refresh time supplied by the service. */
  public void recordRefresh(String repositoryId, String timestamp) {
    jdbc.update("UPDATE repositories SET last_refreshed_at=? WHERE id=?", timestamp, repositoryId);
  }

  /**
   * Deletes the registration and cascades its indexed titles and local state; files stay intact.
   */
  public void delete(String repositoryId) {
    jdbc.update("DELETE FROM repositories WHERE id=?", repositoryId);
  }
}
