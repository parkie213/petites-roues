package app.petitesroues.support;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Initialises test databases using migrations supplied on the consuming test's classpath. */
public final class DatabaseFixtures {
  private DatabaseFixtures() {}

  /** Configures Flyway without applying migrations; the consuming application owns the SQL. */
  public static Flyway flyway(DataSource source) {
    return Flyway.configure().dataSource(source).load();
  }

  /** Applies the consuming test's migrations to its database through the normal Flyway path. */
  public static void migrate(DataSource source) {
    flyway(source).migrate();
  }
}
