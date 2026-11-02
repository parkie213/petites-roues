package db.migration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One-time adoption of catalogues created before Flyway tracked schema versions. Preserves entry
 * identities, queue decisions, progress and AUTOINCREMENT history. Flyway owns the transaction;
 * malformed snapshots roll back all changes in this migration and can be corrected before retry.
 * Future schema changes belong in new numbered SQL migrations, not this compatibility handoff.
 */
public final class V2__Adopt_unversioned_catalogues extends BaseJavaMigration {
  @Override
  public void migrate(Context context) {
    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    var titles = columns(jdbc, "titles");
    if (!titles.contains("document_id"))
      jdbc.execute("ALTER TABLE titles ADD COLUMN document_id TEXT");
    jdbc.update("UPDATE titles SET document_id=id WHERE document_id IS NULL");
    if (!titles.contains("document_type"))
      jdbc.execute("ALTER TABLE titles ADD COLUMN document_type TEXT NOT NULL DEFAULT 'video'");
    var queue = columns(jdbc, "review_queue");
    if (!queue.contains("reviewed_at"))
      jdbc.execute("ALTER TABLE review_queue ADD COLUMN reviewed_at TEXT");
    if (!queue.contains("outcome"))
      jdbc.execute("ALTER TABLE review_queue ADD COLUMN outcome TEXT");
    compactQueue(jdbc, mapper);
  }

  private static Set<String> columns(JdbcTemplate jdbc, String table) {
    return Set.copyOf(
        jdbc.query("PRAGMA table_info(" + table + ")", (row, index) -> row.getString("name")));
  }

  private static void compactQueue(JdbcTemplate jdbc, ObjectMapper mapper) {
    var columns =
        jdbc.query("PRAGMA table_info(review_queue)", (row, index) -> row.getString("name"));
    if (!columns.contains("before_json") && !columns.contains("after_json")) return;
    if (!columns.containsAll(List.of("before_json", "after_json")))
      throw new IllegalStateException("Incomplete legacy review queue schema");
    jdbc.execute("ALTER TABLE review_queue ADD COLUMN cue_ids_json TEXT NOT NULL DEFAULT '[]'");
    jdbc.execute("ALTER TABLE review_queue ADD COLUMN suggestion TEXT");
    var rows =
        jdbc.queryForList(
            "SELECT sequence,kind,before_json,after_json FROM review_queue ORDER BY sequence");
    for (var row : rows) {
      JsonNode before = mapper.readTree((String) row.get("before_json"));
      JsonNode after = mapper.readTree((String) row.get("after_json"));
      if (before == null || after == null || !before.isArray() || !after.isArray())
        throw new IllegalArgumentException(
            "Invalid legacy queue snapshots for sequence " + row.get("sequence"));
      var ids = new LinkedHashSet<String>();
      for (var cues : List.of(before, after))
        for (var cue : cues) {
          var id = cue.path("id");
          if (!id.isTextual() || id.asText().isBlank())
            throw new IllegalArgumentException(
                "Missing legacy queue cue ID for sequence " + row.get("sequence"));
          ids.add(id.asText());
        }
      String suggestion = "suggestion".equals(row.get("kind")) ? suggestion(before, after) : null;
      jdbc.update(
          "UPDATE review_queue SET cue_ids_json=?,suggestion=? WHERE sequence=?",
          mapper.writeValueAsString(ids),
          suggestion,
          row.get("sequence"));
    }
    jdbc.execute("ALTER TABLE review_queue DROP COLUMN before_json");
    jdbc.execute("ALTER TABLE review_queue DROP COLUMN after_json");
  }

  /** Extracts only the newly appended suggestion from the old listening-check annotation. */
  private static String suggestion(JsonNode before, JsonNode after) {
    for (var cue : after) {
      String previous = null;
      for (var original : before)
        if (original.path("id").equals(cue.path("id"))) {
          previous = original.path("listeningCheck").asText(null);
          break;
        }
      String check = cue.path("listeningCheck").asText(null);
      if (check == null) continue;
      if (previous != null && check.startsWith(previous + "\n"))
        check = check.substring(previous.length() + 1);
      String prefix = "Suggested correction (unverified): ";
      if (check.startsWith(prefix)) check = check.substring(prefix.length());
      if (!check.isBlank()) return check;
    }
    throw new IllegalArgumentException("Legacy suggestion entry has no suggestion text");
  }
}
