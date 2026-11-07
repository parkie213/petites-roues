package com.petitesroues.support;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Initializes test databases through the same migrations as startup. */
public final class DatabaseFixtures {
  private DatabaseFixtures() {}

  public static Flyway flyway(DataSource source) {
    return Flyway.configure().dataSource(source).load();
  }

  public static void migrate(DataSource source) {
    flyway(source).migrate();
  }
}
