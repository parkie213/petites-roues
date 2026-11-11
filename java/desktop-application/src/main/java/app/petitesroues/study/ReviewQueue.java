package app.petitesroues.study;

import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.CueChange;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores queued player edits, the review batches that claim them and retained decisions. The
 * document files are never written here; Git checkpoints them.
 */
@Component
public class ReviewQueue {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ReviewQueue(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  /** An open or completed claim on queued edits, bound to the file it was claimed against. */
  public record Batch(long id, String baseSha256, String createdAt, String completedAt) {}

  /** Pending edits in sequence order, without replay state. */
  public List<CueChange> read(CatalogueEntry title) {
    return read(title, false);
  }

  /** Returns completed tasks and their decisions, in sequence order. */
  public List<CueChange> history(CatalogueEntry title) {
    return read(title, true);
  }

  private List<CueChange> read(CatalogueEntry title, boolean completed) {
    return jdbc.query(
        "SELECT * FROM review_queue WHERE title_id=? AND reviewed_at IS "
            + (completed ? "NOT NULL" : "NULL")
            + " ORDER BY sequence",
        (r, i) -> {
          String command = r.getString("command");
          Object batch = r.getObject("batch_id");
          return new CueChange(
              r.getLong("sequence"),
              r.getString("timestamp"),
              r.getString("kind"),
              Arrays.asList(mapper.readValue(r.getString("cue_ids_json"), String[].class)),
              r.getString("suggestion"),
              r.getString("reviewed_at"),
              r.getString("outcome"),
              batch == null ? null : ((Number) batch).longValue(),
              null,
              command == null ? null : mapper.readValue(command, DocumentEdit.class));
        },
        title.id());
  }

  /** Inserts one edit with its affected cue IDs; a failed insert leaves no partial entry. */
  public void append(CatalogueEntry title, DocumentEdit edit) {
    jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json,suggestion,command) VALUES(?,?,?,?,?,?)",
        title.id(),
        Instant.now().toString(),
        edit.kind(),
        mapper.writeValueAsString(edit.cueIds()),
        edit.suggestion(),
        mapper.writeValueAsString(edit));
  }

  public Optional<Batch> batch(CatalogueEntry title, long id) {
    return jdbc
        .query(
            "SELECT * FROM review_batches WHERE title_id=? AND id=?",
            (r, i) ->
                new Batch(
                    r.getLong("id"),
                    r.getString("base_sha256"),
                    r.getString("created_at"),
                    r.getString("completed_at")),
            title.id(),
            id)
        .stream()
        .findFirst();
  }

  public Optional<Batch> openBatch(CatalogueEntry title) {
    return jdbc
        .query(
            "SELECT id FROM review_batches WHERE title_id=? AND completed_at IS NULL",
            (r, i) -> r.getLong("id"),
            title.id())
        .stream()
        .findFirst()
        .flatMap(id -> batch(title, id));
  }

  /**
   * Creates a batch claiming the oldest unclaimed pending edits; the caller owns the transaction.
   */
  public Batch claim(CatalogueEntry title, String baseSha256, int limit) {
    String now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO review_batches(title_id,base_sha256,created_at) VALUES(?,?,?)",
        title.id(),
        baseSha256,
        now);
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    jdbc.update(
        "UPDATE review_queue SET batch_id=? WHERE sequence IN (SELECT sequence FROM review_queue"
            + " WHERE title_id=? AND reviewed_at IS NULL AND batch_id IS NULL ORDER BY sequence LIMIT ?)",
        id,
        title.id(),
        limit);
    return new Batch(id, baseSha256, now, null);
  }

  /** Records decisions for a batch's edits and closes it; the caller owns the transaction. */
  public void complete(CatalogueEntry title, long batch, Map<Long, String> outcomes, String at) {
    for (var outcome : outcomes.entrySet())
      jdbc.update(
          "UPDATE review_queue SET reviewed_at=?,outcome=? WHERE title_id=? AND sequence=? AND batch_id=? AND reviewed_at IS NULL",
          at,
          outcome.getValue(),
          title.id(),
          outcome.getKey(),
          batch);
    jdbc.update(
        "UPDATE review_batches SET completed_at=? WHERE title_id=? AND id=?",
        at,
        title.id(),
        batch);
  }

  /** Deletes pending edits and closes any open batch; the caller owns the transaction. */
  public void discard(CatalogueEntry title) {
    jdbc.update("DELETE FROM review_queue WHERE title_id=? AND reviewed_at IS NULL", title.id());
    jdbc.update(
        "UPDATE review_batches SET completed_at=? WHERE title_id=? AND completed_at IS NULL",
        Instant.now().toString(),
        title.id());
  }

  /** A title with pending queue rows, for agents choosing what to process. */
  public record PendingTitle(String titleId, String title, int pending, Long openBatch) {}

  public List<PendingTitle> pendingTitles() {
    return jdbc.query(
        "SELECT t.id, t.title, count(*) AS pending,"
            + " (SELECT b.id FROM review_batches b WHERE b.title_id=t.id AND b.completed_at IS NULL) AS open_batch"
            + " FROM review_queue q JOIN titles t ON t.id=q.title_id WHERE q.reviewed_at IS NULL"
            + " GROUP BY t.id, t.title ORDER BY min(q.sequence)",
        (r, i) -> {
          Object batch = r.getObject("open_batch");
          return new PendingTitle(
              r.getString("id"),
              r.getString("title"),
              r.getInt("pending"),
              batch == null ? null : ((Number) batch).longValue());
        });
  }

  /** Pending edits not yet written into any file of the repository's titles. */
  public int unwrittenEdits(String repositoryId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM review_queue q JOIN titles t ON t.id=q.title_id"
            + " WHERE t.repository_id=? AND q.reviewed_at IS NULL AND q.command IS NOT NULL",
        Integer.class,
        repositoryId);
  }
}
