package com.petitesroues.library.persistence;

import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The local library index. Services own transactions; lookups never scan folders or parse
 * documents.
 */
@Repository
public class CatalogueStore {
  private static final String SELECT =
      "SELECT t.*,r.name AS repository_name,r.local_path AS repository_path FROM titles t JOIN repositories r ON t.repository_id=r.id";
  private final JdbcTemplate jdbc;

  public CatalogueStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private CatalogueEntry mapTitle(ResultSet row) throws SQLException {
    return new CatalogueEntry(
        row.getString("id"),
        row.getString("document_id"),
        row.getString("title"),
        row.getString("series"),
        (Integer) row.getObject("season"),
        (Integer) row.getObject("episode"),
        (Integer) row.getObject("volume"),
        row.getString("source_url"),
        row.getString("document_path"),
        new CatalogueLocation(
            row.getString("repository_id"),
            row.getString("repository_name"),
            row.getString("repository_path"),
            row.getBoolean("missing"),
            (Double) row.getObject("duration_seconds"),
            row.getString("document_type"),
            row.getString("reference_fingerprint")));
  }

  public List<CatalogueEntry> all() {
    return jdbc.query(SELECT + " ORDER BY t.id", (row, index) -> mapTitle(row));
  }

  /** Looks up a local entry without accessing its files; unknown IDs return empty. */
  public Optional<CatalogueEntry> find(String id) {
    return jdbc.query(SELECT + " WHERE t.id = ?", (row, index) -> mapTitle(row), id).stream()
        .findFirst();
  }

  /** Finds a portable document within one repository, including missing entries. */
  public Optional<CatalogueEntry> findDocument(String repositoryId, String documentId) {
    return jdbc
        .query(
            SELECT + " WHERE t.document_id = ? AND t.repository_id = ?",
            (row, index) -> mapTitle(row),
            documentId,
            repositoryId)
        .stream()
        .findFirst();
  }

  public boolean contains(String id) {
    return !jdbc.queryForList("SELECT id FROM titles WHERE id = ?", String.class, id).isEmpty();
  }

  /** Saves a repository-owned title and its reference metadata, retaining its local identity. */
  public void saveTitle(CatalogueEntry title) {
    jdbc.update(
        """
        INSERT INTO titles (
          id, document_id, title, series, season, episode, volume, source_url,
          document_path, repository_id, missing, duration_seconds, document_type,
          reference_fingerprint
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          document_id = excluded.document_id,
          title = excluded.title,
          series = excluded.series,
          season = excluded.season,
          episode = excluded.episode,
          volume = excluded.volume,
          source_url = excluded.source_url,
          document_path = excluded.document_path,
          repository_id = excluded.repository_id,
          missing = excluded.missing,
          duration_seconds = excluded.duration_seconds,
          document_type = excluded.document_type,
          reference_fingerprint = excluded.reference_fingerprint
        """,
        title.id(),
        title.documentId(),
        title.title(),
        title.series(),
        title.season(),
        title.episode(),
        title.volume(),
        title.sourceUrl(),
        title.documentPath(),
        title.location().repositoryId(),
        title.location().missing(),
        title.location().durationSeconds(),
        title.location().documentType(),
        title.location().referenceFingerprint());
  }
}
