package app.petitesroues.study;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists immutable command receipts inside the application's edit transaction. */
@Repository
public class CommandReceiptStore {
  private final JdbcTemplate jdbc;

  public CommandReceiptStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Receipt(String request, String result) {}

  public Optional<Receipt> find(String title, String request) {
    return jdbc
        .query(
            "SELECT request_json,result_json FROM command_receipts WHERE title_id=? AND request_id=?",
            (row, i) -> new Receipt(row.getString(1), row.getString(2)),
            title,
            request)
        .stream()
        .findFirst();
  }

  public void insert(String title, String request, String payload, String result) {
    jdbc.update("INSERT INTO command_receipts VALUES (?,?,?,?)", title, request, payload, result);
  }
}
