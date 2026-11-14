package app.petitesroues.library.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deletes the rebuildable library data while keeping what the owner configured. Owns this SQL; the
 * caller owns the transaction and decides when a wipe is allowed.
 */
@Repository
public class LibraryResetStore {
  private final JdbcTemplate jdbc;

  public LibraryResetStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Deletes every row of every table except {@code repositories}, {@code settings} and Flyway's
   * history, in the caller's transaction. Deleting {@code titles} cascades to accepted and served
   * copies, review batches and queue rows, both progress tables and title-bound media links; {@code
   * media}, {@code media_links} and {@code phones} are deleted explicitly. A table added later must
   * be wiped here or deliberately kept; the store's test fails until it is.
   */
  public void wipe() {
    jdbc.update("DELETE FROM titles");
    jdbc.update("DELETE FROM media_links");
    jdbc.update("DELETE FROM media");
    jdbc.update("DELETE FROM phones");
  }
}
