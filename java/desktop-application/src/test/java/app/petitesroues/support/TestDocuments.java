package app.petitesroues.support;

import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.editing.VideoCueEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.CatalogueLocation;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared desktop test fixtures for catalogue registration and editable cue inputs. */
public final class TestDocuments {
  private TestDocuments() {}

  /** Registers the test repository and describes an entry; does not insert the entry itself. */
  public static CatalogueEntry catalogueEntry(JdbcTemplate jdbc, String id, Path root) {
    jdbc.update(
        "INSERT OR IGNORE INTO repositories(id,name,local_path) VALUES('test-repo','Tests',?)",
        root.toString());
    return new CatalogueEntry(
        id,
        id,
        "Title",
        null,
        null,
        null,
        null,
        null,
        id + "/document.json",
        new CatalogueLocation("test-repo", "Tests", root.toString(), false, null));
  }

  /** Copies an existing cue into an edit request, preserving its text and check flags. */
  public static VideoCueEdit edit(VideoCue cue) {
    var line = cue.line();
    return new VideoCueEdit(
        cue.start(),
        cue.end(),
        line.source(),
        line.translation(),
        line.notes(),
        line.continuesPrevious(),
        cue.listeningCheck(),
        line.textCheck());
  }
}
