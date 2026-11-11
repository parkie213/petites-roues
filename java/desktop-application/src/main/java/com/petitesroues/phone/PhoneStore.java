package com.petitesroues.phone;

import com.petitesroues.model.phone.PairedPhone;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Paired phones ({@link PairedPhone}) and their token hashes. Owns their SQL. */
@Repository
public class PhoneStore {
  private static final String SELECT = "SELECT id, name, paired_at, last_seen_at FROM phones";
  private final JdbcTemplate jdbc;

  public PhoneStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static PairedPhone map(java.sql.ResultSet row) throws java.sql.SQLException {
    return new PairedPhone(
        row.getString("id"),
        row.getString("name"),
        row.getString("paired_at"),
        row.getString("last_seen_at"));
  }

  /** Records a newly paired phone. */
  public void insert(PairedPhone phone, String tokenSha256) {
    jdbc.update(
        "INSERT INTO phones (id, name, token_sha256, paired_at) VALUES (?, ?, ?, ?)",
        phone.id(),
        phone.name(),
        tokenSha256,
        phone.pairedAt());
  }

  /** The phone holding the token with this hash, or empty. */
  public Optional<PairedPhone> withToken(String tokenSha256) {
    return jdbc.query(SELECT + " WHERE token_sha256=?", (row, i) -> map(row), tokenSha256).stream()
        .findFirst();
  }

  /** Records the phone's latest authenticated request. */
  public void seen(String id, String at) {
    jdbc.update("UPDATE phones SET last_seen_at=? WHERE id=?", at, id);
  }

  /** Every paired phone, oldest first. */
  public List<PairedPhone> all() {
    return jdbc.query(SELECT + " ORDER BY paired_at, id", (row, i) -> map(row));
  }

  /** Forgets the phone, revoking its token; returns whether it was paired. */
  public boolean delete(String id) {
    return jdbc.update("DELETE FROM phones WHERE id=?", id) > 0;
  }
}
