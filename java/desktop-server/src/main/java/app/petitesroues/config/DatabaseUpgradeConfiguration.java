package app.petitesroues.config;

import app.petitesroues.library.DocumentImportService;
import app.petitesroues.study.ReviewQueueService;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Startup steps around the catalogue migration: refusing an upgrade that would strand pending
 * player edits, giving titles that predate accepted copies theirs once migrated, and then building
 * every title's served copy.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseUpgradeConfiguration {
  private static final Logger log = LoggerFactory.getLogger(DatabaseUpgradeConfiguration.class);

  /** The schema version from which pending queue rows are no longer replayed. */
  private static final MigrationVersion FIRST_ACCEPTED_VERSION = MigrationVersion.fromVersion("3");

  /** Runs {@link #refuseUpgradeWithPendingReviews} before every migration. */
  @Bean
  public FlywayMigrationStrategy pendingReviewGuard() {
    return flyway -> {
      refuseUpgradeWithPendingReviews(flyway);
      flyway.migrate();
    };
  }

  /**
   * Fails when the database is at schema version 2 or lower and its review queue still holds
   * unreviewed rows. From version 3 the application serves accepted copies instead of replaying the
   * queue over the files, so those rows must be finished or discarded with the previous version
   * first. A fresh or unmanaged database has no queue to protect and is left to Flyway.
   *
   * @throws IllegalStateException if pending rows would be left behind by the upgrade
   */
  public static void refuseUpgradeWithPendingReviews(Flyway flyway) {
    var current = flyway.info().current();
    if (current == null || current.getVersion().compareTo(FIRST_ACCEPTED_VERSION) >= 0) return;
    DataSource source = flyway.getConfiguration().getDataSource();
    var jdbc = new JdbcTemplate(source);
    int tables =
        jdbc.queryForObject(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='review_queue'",
            Integer.class);
    if (tables == 0) return;
    int pending =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_queue WHERE reviewed_at IS NULL", Integer.class);
    if (pending > 0)
      throw new IllegalStateException(
          "The library has "
              + pending
              + " pending review queue rows. Finish or discard the pending review queue with the"
              + " previous version before upgrading.");
  }

  /** Imports every title without an accepted copy once startup has migrated the database. */
  @Bean
  @Order(1)
  public ApplicationRunner acceptedDocumentBackfill(DocumentImportService imports) {
    return arguments -> {
      int imported = imports.backfill();
      if (imported > 0) log.info("Accepted {} existing documents", imported);
    };
  }

  /**
   * Builds the served copy of every title that lacks a current one or has pending edits, after the
   * accepted copies exist. Until it has run, a read rebuilds the title it asks for.
   */
  @Bean
  @Order(2)
  public ApplicationRunner servedDocumentRebuild(ReviewQueueService review) {
    return arguments -> {
      int rebuilt = review.rebuildServedCopies();
      if (rebuilt > 0) log.info("Rebuilt the served copy of {} documents", rebuilt);
    };
  }
}
