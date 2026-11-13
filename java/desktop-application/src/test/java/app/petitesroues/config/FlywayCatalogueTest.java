package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.support.DatabaseFixtures;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class FlywayCatalogueTest {
  @Test
  void freshDatabaseIsCreatedAndTrackedByFlywayOnlyOnce() {
    var source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("PRAGMA foreign_keys=ON");
      var flyway = DatabaseFixtures.flyway(source);
      assertEquals(3, flyway.migrate().migrationsExecuted);
      assertEquals(0, flyway.migrate().migrationsExecuted);
      flyway.validate();
      assertEquals(
          9,
          jdbc.queryForObject(
              "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('repositories','titles','media','review_batches','review_queue','playback_progress','reading_progress','command_receipts','flyway_schema_history')",
              Integer.class));
      assertTrue(
          jdbc.queryForList("PRAGMA table_info(media)").stream()
              .anyMatch(column -> "audio_languages".equals(column.get("name"))));
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
  void aCatalogueFromBeforeTheSchemaIsRefusedRatherThanAdopted() {
    var source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("CREATE TABLE titles (id TEXT PRIMARY KEY)");
      assertThrows(FlywayException.class, () -> DatabaseFixtures.flyway(source).migrate());
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='media'",
              Integer.class),
          "nothing is created over an old catalogue");
    } finally {
      source.destroy();
    }
  }
}
