package app.petitesroues.config;

import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a stable operation marker to startup migration failures without changing Flyway behavior.
 */
@Configuration(proxyBeanMethods = false)
public class MigrationConfiguration {
  private static final System.Logger LOG = System.getLogger(MigrationConfiguration.class.getName());

  /** Runs normal validation/migration and propagates failures so startup cannot continue. */
  @Bean
  public FlywayMigrationStrategy catalogueMigrationStrategy() {
    return flyway -> {
      try {
        flyway.migrate();
      } catch (RuntimeException failure) {
        // Do not copy SQL, connection credentials or nested exception messages into this marker.
        LOG.log(
            System.Logger.Level.ERROR,
            "operation=catalogue.migrate failure={0} code={1}",
            failure.getClass().getName(),
            failure instanceof FlywayException migration ? migration.getErrorCode() : "unknown");
        throw failure;
      }
    };
  }
}
