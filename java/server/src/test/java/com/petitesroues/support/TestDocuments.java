package com.petitesroues.support;

import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.editing.VideoCueEdit;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueLocation;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TestDocuments {
  private TestDocuments() {}

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
        new CatalogueLocation("test-repo", "Tests", root.toString(), false, null, null));
  }

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
