package app.petitesroues.library.persistence;

import app.petitesroues.model.library.MediaLink;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Manual media links ({@link MediaLink}). Owns their SQL; {@link
 * app.petitesroues.library.MediaLinkService} validates and decides. A link goes with its folder or
 * its title when either is removed.
 */
@Repository
public class MediaLinkStore {
  private final JdbcTemplate jdbc;

  public MediaLinkStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Every manual link, ordered by folder and path. */
  public List<MediaLink> all() {
    return jdbc.query(
        "SELECT repository_id, relative_path, title_id FROM media_links"
            + " ORDER BY repository_id, relative_path",
        (row, i) ->
            new MediaLink(
                row.getString("repository_id"),
                row.getString("relative_path"),
                row.getString("title_id")));
  }

  /** Links the file to the title, replacing any link the file had. */
  public void link(MediaLink link, String linkedAt) {
    jdbc.update(
        """
        INSERT INTO media_links (repository_id, relative_path, title_id, linked_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (repository_id, relative_path)
        DO UPDATE SET title_id=excluded.title_id, linked_at=excluded.linked_at
        """,
        link.repositoryId(),
        link.relativePath(),
        link.titleId(),
        linkedAt);
  }

  /** Removes the file's link; returns whether it had one. */
  public boolean unlink(String repositoryId, String relativePath) {
    return jdbc.update(
            "DELETE FROM media_links WHERE repository_id=? AND relative_path=?",
            repositoryId,
            relativePath)
        > 0;
  }
}
