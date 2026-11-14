package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DatabaseUpgradeConfigurationTest {
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;

  @BeforeEach
  void setup() {
    source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    jdbc = new JdbcTemplate(source);
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  private static Flyway flyway(DataSource source, String target) {
    return Flyway.configure().dataSource(source).target(target).load();
  }

  private void upgrade() {
    new DatabaseUpgradeConfiguration().pendingReviewGuard().migrate(flyway(source, "latest"));
  }

  private String version() {
    return jdbc.queryForObject(
        "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
        String.class);
  }

  private void insertReview(String reviewedAt) {
    jdbc.update("INSERT INTO repositories(id,name,local_path) VALUES('r','r','/r')");
    jdbc.update(
        "INSERT INTO titles(id,repository_id,document_id,title,document_path) VALUES('t','r','t','t','t.json')");
    jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json,reviewed_at)"
            + " VALUES('t','now','edit','[]',?)",
        reviewedAt);
  }

  @Test
  void aVersionTwoDatabaseWithPendingReviewRowsIsNotUpgraded() {
    flyway(source, "2").migrate();
    insertReview(null);

    var refusal = assertThrows(IllegalStateException.class, this::upgrade);

    assertTrue(refusal.getMessage().contains("pending review queue"));
    assertEquals("2", version());
  }

  @Test
  void aVersionTwoDatabaseWithNoPendingRowsIsUpgraded() {
    flyway(source, "2").migrate();
    insertReview("done");

    upgrade();

    assertEquals("4", version());
  }

  @Test
  void aVersionThreeDatabaseWithPendingReviewRowsIsUpgradedAndKeepsThem() {
    flyway(source, "3").migrate();
    insertReview(null);

    upgrade();

    assertEquals("4", version());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM review_queue WHERE reviewed_at IS NULL AND conflict=0",
            Integer.class));
  }

  @Test
  void aFreshDatabaseIsMigrated() {
    upgrade();

    assertEquals("4", version());
  }

  @Test
  void anUpgradedDatabaseIsNotCheckedAgain() {
    upgrade();
    insertReview(null);

    upgrade();

    assertEquals("4", version());
  }
}
