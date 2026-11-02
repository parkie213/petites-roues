package com.petitesroues.support;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Initializes test databases through the same ordered migrations and legacy baseline as startup.
 */
public final class DatabaseFixtures {
  private DatabaseFixtures() {}

  public static Flyway flyway(DataSource source) {
    return Flyway.configure()
        .dataSource(source)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();
  }

  public static void migrate(DataSource source) {
    flyway(source).migrate();
  }
}
