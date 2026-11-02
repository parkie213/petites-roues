package com.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.support.DatabaseFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class FlywayCatalogueTest {
  @Test
  void migrationPreservesEntryIdsMembershipsReviewsAndProgressAndCanRunTwice() {
    var source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("PRAGMA foreign_keys=ON");
      jdbc.execute(
          "CREATE TABLE titles(id TEXT PRIMARY KEY,repository_id TEXT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,title TEXT NOT NULL,document_path TEXT NOT NULL)");
      new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__Create_catalogue.sql"))
          .execute(source);
      jdbc.update(
          "INSERT INTO repositories(id,name,local_path) VALUES('repo','Repository','/tmp')");
      jdbc.update(
          "INSERT INTO titles(id,repository_id,title,document_path) VALUES('old-id','repo','Title','document.json')");
      jdbc.update(
          "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json,suggestion) VALUES('old-id','time','edit','[]',NULL)");
      jdbc.update("INSERT INTO playback_progress VALUES('old-id',123.5,'time')");
      jdbc.execute("ALTER TABLE review_queue DROP COLUMN reviewed_at");
      jdbc.execute("ALTER TABLE review_queue DROP COLUMN outcome");
      var reviews = jdbc.queryForList("SELECT * FROM review_queue");
      var progress = jdbc.queryForList("SELECT * FROM playback_progress");
      var membership = jdbc.queryForList("SELECT repository_id FROM titles");
      var migration = DatabaseFixtures.flyway(source);
      assertEquals(5, migration.migrate().migrationsExecuted);
      assertEquals(0, migration.migrate().migrationsExecuted);
      assertEquals(
          "old-id",
          jdbc.queryForObject("SELECT document_id FROM titles WHERE id='old-id'", String.class));
      assertEquals(
          reviews,
          jdbc.queryForList(
              "SELECT sequence,title_id,timestamp,kind,cue_ids_json,suggestion FROM review_queue"));
      assertNull(jdbc.queryForObject("SELECT reviewed_at FROM review_queue", String.class));
      assertNull(jdbc.queryForObject("SELECT outcome FROM review_queue", String.class));
      assertEquals("video", jdbc.queryForObject("SELECT document_type FROM titles", String.class));
      assertEquals(progress, jdbc.queryForList("SELECT * FROM playback_progress"));
      assertEquals(membership, jdbc.queryForList("SELECT repository_id FROM titles"));
    } finally {
      source.destroy();
    }
  }

  @Test
  void freshDatabaseIsCreatedAndTrackedByFlywayOnlyOnce() {
    var source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("PRAGMA foreign_keys=ON");
      var flyway = DatabaseFixtures.flyway(source);
      assertEquals(5, flyway.migrate().migrationsExecuted);
      assertEquals(0, flyway.migrate().migrationsExecuted);
      flyway.validate();
      assertEquals(
          5,
          jdbc.queryForObject(
              "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('repositories','titles','review_queue','playback_progress','reading_progress')",
              Integer.class));
      assertEquals(
          5,
          jdbc.queryForObject(
              "SELECT count(*) FROM flyway_schema_history WHERE success=1", Integer.class));
      assertTrue(jdbc.queryForList("PRAGMA foreign_key_check").isEmpty());
      var error =
          assertThrows(
              DataAccessException.class,
              () ->
                  jdbc.update(
                      "INSERT INTO reading_progress(title_id,page_id,updated_at) VALUES('unknown','page-1','time')"));
      assertTrue(
          error.getMostSpecificCause().getMessage().contains("FOREIGN KEY constraint failed"));
    } finally {
      source.destroy();
    }
  }

  @Test
  void adoptingCurrentSchemaRetainsEveryRowAndDoesNotRerunTheHandoff() {
    var source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("PRAGMA foreign_keys=ON");
      new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__Create_catalogue.sql"))
          .execute(source);
      jdbc.update("INSERT INTO repositories VALUES('repo','Repository','/tmp','last-refresh')");
      jdbc.update(
          "INSERT INTO titles(id,repository_id,document_id,title,document_path,document_type) VALUES('entry','repo','portable-id','Title','document.json','comic')");
      jdbc.update(
          "INSERT INTO review_queue VALUES(42,'entry','time','edit','[\"cue\"]',NULL,'decision','applied')");
      jdbc.update("INSERT INTO playback_progress VALUES('entry',123.5,'time')");
      jdbc.update("INSERT INTO reading_progress VALUES('entry','page-3','time')");
      var tables =
          List.of(
              "repositories",
              "titles",
              "review_queue",
              "playback_progress",
              "reading_progress",
              "sqlite_sequence");
      var before = new LinkedHashMap<String, Object>();
      for (var table : tables) before.put(table, jdbc.queryForList("SELECT * FROM " + table));
      var flyway = DatabaseFixtures.flyway(source);
      assertEquals(5, flyway.migrate().migrationsExecuted);
      assertEquals(0, flyway.migrate().migrationsExecuted);
      for (var table : tables) {
        var after = jdbc.queryForList("SELECT * FROM " + table);
        // Rows queued before V3 describe edits already in the files: never replayed.
        if (table.equals("review_queue"))
          for (var row : after) {
            assertNull(row.remove("command"));
            assertNull(row.remove("batch_id"));
          }
        // V4 adds the comic volume; Refresh fills it from the documents.
        if (table.equals("titles")) for (var row : after) assertNull(row.remove("volume"));
        // V5 adds the comic reading fraction; the next save fills it.
        if (table.equals("reading_progress"))
          for (var row : after) assertNull(row.remove("fraction"));
        assertEquals(before.get(table), after, table);
      }
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT count(*) FROM flyway_schema_history WHERE version='2' AND success=1",
              Integer.class));
      assertTrue(jdbc.queryForList("PRAGMA foreign_key_check").isEmpty());
    } finally {
      source.destroy();
    }
  }
}
