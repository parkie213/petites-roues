package app.petitesroues.settings;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Small application settings chosen in the UI, as text by key. Owns their SQL. */
@Repository
public class SettingsStore {
  private final JdbcTemplate jdbc;

  public SettingsStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The setting's value, or empty when it was never set. */
  public Optional<String> get(String key) {
    return jdbc.queryForList("SELECT value FROM settings WHERE key=?", String.class, key).stream()
        .findFirst();
  }

  /** Sets the setting, replacing any earlier value. */
  public void put(String key, String value) {
    jdbc.update(
        "INSERT INTO settings (key, value) VALUES (?, ?)"
            + " ON CONFLICT (key) DO UPDATE SET value=excluded.value",
        key,
        value);
  }
}
