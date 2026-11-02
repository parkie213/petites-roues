package com.petitesroues.config;

import static com.petitesroues.support.TestDocuments.catalogueEntry;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.editing.DocumentEdit;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.study.ReviewQueue;
import com.petitesroues.support.DatabaseFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

class FlywayLegacyQueueTest {
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;
  Flyway migration;
  CatalogueEntry title =
      new CatalogueEntry("entry", "Title", null, null, null, null, "/document.json");
  JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute("PRAGMA foreign_keys=ON");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__Create_catalogue.sql"))
        .execute(source);
    title = catalogueEntry(jdbc, "entry", Path.of("/test"));
    // A pre-V4 row, written with the V1 columns rather than today's CatalogueStore.
    jdbc.update(
        "INSERT INTO titles(id,repository_id,document_id,title,document_path) VALUES(?,?,?,?,?)",
        title.id(),
        title.location().repositoryId(),
        title.documentId(),
        title.title(),
        title.documentPath());
    jdbc.execute("DROP TABLE review_queue");
    jdbc.execute(
        """
        CREATE TABLE review_queue (
          sequence INTEGER PRIMARY KEY AUTOINCREMENT,
          title_id TEXT NOT NULL REFERENCES titles(id) ON DELETE CASCADE,
          timestamp TEXT NOT NULL, kind TEXT NOT NULL,
          before_json TEXT NOT NULL, after_json TEXT NOT NULL,
          reviewed_at TEXT, outcome TEXT)
        """);
    migration = DatabaseFixtures.flyway(source);
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  private void insert(long sequence, String kind, List<?> before, List<?> after) {
    jdbc.update(
        "INSERT INTO review_queue VALUES(?,?,?,?,?,?,?,?)",
        sequence,
        title.id(),
        "time-" + sequence,
        kind,
        mapper.writeValueAsString(before),
        mapper.writeValueAsString(after),
        null,
        null);
  }

  @Test
  void migrationKeepsTaskIdentitiesAndDecisionsAndExtractsOnlyIdsAndEachSuggestion() {
    var a = Map.of("id", "a", "fr", "Old text", "notes", List.of("Old note"));
    var b = Map.of("id", "b");
    insert(1, "edit", List.of(a), List.of(a));
    insert(2, "split", List.of(a), List.of(a, b));
    insert(3, "merge", List.of(a, b), List.of(a));
    insert(4, "delete", List.of(Map.of("id", "gone")), List.of());
    insert(5, "add", List.of(), List.of(Map.of("id", "new")));
    String first = "Original uncertainty\nSuggested correction (unverified): First proposal";
    String second = first + "\nSuggested correction (unverified): Second proposal";
    insert(
        6,
        "suggestion",
        List.of(Map.of("id", "a", "listeningCheck", "Original uncertainty")),
        List.of(Map.of("id", "a", "listeningCheck", first)));
    insert(
        7,
        "suggestion",
        List.of(Map.of("id", "a", "listeningCheck", first)),
        List.of(Map.of("id", "a", "listeningCheck", second)));
    insert(
        8,
        "suggestion",
        List.of(Map.of("id", "a")),
        List.of(
            Map.of(
                "id", "a", "listeningCheck", "Suggested correction (unverified): Fresh proposal")));
    jdbc.update(
        "UPDATE review_queue SET reviewed_at='decision-time',outcome='applied' WHERE sequence=1");

    migration.migrate();
    var rows = jdbc.queryForList("SELECT * FROM review_queue ORDER BY sequence");
    migration.migrate();
    assertEquals(rows, jdbc.queryForList("SELECT * FROM review_queue ORDER BY sequence"));
    var columns =
        jdbc.query("PRAGMA table_info(review_queue)", (row, index) -> row.getString("name"));
    assertFalse(columns.contains("before_json"));
    assertFalse(columns.contains("after_json"));
    var queue = new ReviewQueue(jdbc, mapper);
    var pending = queue.read(title);
    assertEquals(
        List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L), pending.stream().map(c -> c.sequence()).toList());
    assertEquals(List.of("a", "b"), pending.get(0).cueIds());
    assertEquals(List.of("a", "b"), pending.get(1).cueIds());
    assertEquals(List.of("gone"), pending.get(2).cueIds());
    assertEquals(List.of("new"), pending.get(3).cueIds());
    assertNull(pending.get(0).suggestion());
    assertEquals("First proposal", pending.get(4).suggestion());
    assertEquals("Second proposal", pending.get(5).suggestion());
    assertEquals("Fresh proposal", pending.get(6).suggestion());
    var completed = queue.history(title).getFirst();
    assertEquals(1, completed.sequence());
    assertEquals("time-1", completed.timestamp());
    assertEquals("edit", completed.kind());
    assertEquals("decision-time", completed.reviewedAt());
    assertEquals("applied", completed.outcome());
    assertEquals(List.of("a"), completed.cueIds());
  }

  @Test
  void emptyQueueKeepsItsSequenceHighWaterMark() throws Exception {
    insert(100, "delete", List.of(Map.of("id", "removed")), List.of());
    jdbc.update("DELETE FROM review_queue");
    migration.migrate();
    var queue = new ReviewQueue(jdbc, mapper);
    queue.append(title, new DocumentEdit.DeleteVideoCue("cue"));
    assertEquals(101, queue.read(title).getFirst().sequence());
  }

  @Test
  void malformedLegacyDataRollsBackBothSchemaAndEarlierConvertedRows() {
    insert(1, "edit", List.of(Map.of("id", "a")), List.of(Map.of("id", "a")));
    insert(2, "edit", List.of(), List.of());
    jdbc.update("UPDATE review_queue SET after_json='not json' WHERE sequence=2");
    jdbc.execute("ALTER TABLE review_queue DROP COLUMN reviewed_at");
    jdbc.execute("ALTER TABLE review_queue DROP COLUMN outcome");
    var before = jdbc.queryForList("SELECT * FROM review_queue ORDER BY sequence");
    var schema = jdbc.queryForList("PRAGMA table_info(review_queue)");
    assertThrows(RuntimeException.class, () -> migration.migrate());
    assertEquals(before, jdbc.queryForList("SELECT * FROM review_queue ORDER BY sequence"));
    assertEquals(schema, jdbc.queryForList("PRAGMA table_info(review_queue)"));
    jdbc.update("UPDATE review_queue SET after_json='[]' WHERE sequence=2");
    assertDoesNotThrow(() -> migration.migrate());
  }
}
