package app.petitesroues.library.persistence;

import app.petitesroues.model.library.AcceptedDocument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The accepted copy of each title's document. Owns SQL and row mapping only; services decide when a
 * copy may change and own the transaction. A title has at most one copy, removed with the title.
 */
@Repository
public class AcceptedDocumentStore {
  private final JdbcTemplate jdbc;

  public AcceptedDocumentStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The title's accepted bytes, hash and import time, or empty when none has been accepted. */
  public Optional<AcceptedDocument> find(String titleId) {
    return jdbc
        .query(
            "SELECT json, source_sha256, imported_at FROM accepted_documents WHERE title_id=?",
            (row, i) ->
                new AcceptedDocument(
                    row.getBytes("json"),
                    row.getString("source_sha256"),
                    row.getString("imported_at")),
            titleId)
        .stream()
        .findFirst();
  }

  /** The title's accepted hash without loading the document, or empty when none. */
  public Optional<String> findHash(String titleId) {
    return jdbc
        .queryForList(
            "SELECT source_sha256 FROM accepted_documents WHERE title_id=?", String.class, titleId)
        .stream()
        .findFirst();
  }

  /** Accepted hashes of one repository's titles, keyed by portable document ID. */
  public Map<String, String> hashesByDocumentId(String repositoryId) {
    Map<String, String> hashes = new HashMap<>();
    jdbc.query(
        "SELECT t.document_id, a.source_sha256 FROM accepted_documents a"
            + " JOIN titles t ON t.id=a.title_id WHERE t.repository_id=?",
        row -> {
          hashes.put(row.getString(1), row.getString(2));
        },
        repositoryId);
    return hashes;
  }

  /** Inserts or replaces the title's accepted copy. */
  public void save(String titleId, byte[] json, String sha256, String importedAt) {
    jdbc.update(
        """
        INSERT INTO accepted_documents (title_id, source_sha256, json, imported_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(title_id) DO UPDATE SET
          source_sha256 = excluded.source_sha256,
          json = excluded.json,
          imported_at = excluded.imported_at
        """,
        titleId,
        sha256,
        json,
        importedAt);
  }

  /** IDs of titles that have no accepted copy yet, in ID order. */
  public List<String> titlesWithoutCopy() {
    return jdbc.queryForList(
        "SELECT id FROM titles WHERE id NOT IN (SELECT title_id FROM accepted_documents)"
            + " ORDER BY id",
        String.class);
  }
}
