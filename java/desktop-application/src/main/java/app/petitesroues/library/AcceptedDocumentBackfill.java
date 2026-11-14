package app.petitesroues.library;

import app.petitesroues.library.AcceptedDocuments.Result;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The startup step that gives titles predating accepted copies theirs, by importing each title's
 * registered file through {@link DocumentImportService}. It owns which titles are selected and the
 * tolerance for bad files; each import owns its own transaction.
 */
@Service
public class AcceptedDocumentBackfill {
  private static final Logger log = LoggerFactory.getLogger(AcceptedDocumentBackfill.class);

  private final AcceptedDocumentStore accepted;
  private final DocumentImportService imports;

  public AcceptedDocumentBackfill(AcceptedDocumentStore accepted, DocumentImportService imports) {
    this.accepted = accepted;
    this.imports = imports;
  }

  /**
   * Imports every title that has no accepted copy. A title whose file is missing or invalid is
   * logged and left without one; it does not stop the others. Safe to repeat: titles already
   * imported are not selected again.
   *
   * @return how many titles gained an accepted copy
   */
  public int run() {
    int imported = 0;
    for (String titleId : accepted.titlesWithoutCopy()) {
      try {
        if (imports.importFile(titleId) == Result.IMPORTED) imported++;
      } catch (IOException | RuntimeException failure) {
        log.warn("Cannot accept the document of title {}: {}", titleId, failure.toString());
      }
    }
    return imported;
  }
}
