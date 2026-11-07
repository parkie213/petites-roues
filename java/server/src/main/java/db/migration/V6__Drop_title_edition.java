package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Drops the indexed edition: documents no longer carry one, because their reference media
 * (fingerprint, size and duration) identifies the copy they were made against. A catalogue adopted
 * from before Flyway may never have had the column, and SQLite has no {@code DROP COLUMN IF
 * EXISTS}, so this checks first.
 */
public final class V6__Drop_title_edition extends BaseJavaMigration {
  @Override
  public void migrate(Context context) {
    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    var columns = jdbc.query("PRAGMA table_info(titles)", (row, index) -> row.getString("name"));
    if (columns.contains("edition")) jdbc.execute("ALTER TABLE titles DROP COLUMN edition");
  }
}
