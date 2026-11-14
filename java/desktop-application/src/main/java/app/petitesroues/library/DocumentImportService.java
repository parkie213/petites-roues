package app.petitesroues.library;

import app.petitesroues.library.AcceptedDocuments.Result;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.study.DocumentStore;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The explicit import of one title's registered file as its accepted copy. The other ways a file
 * becomes an accepted copy, Add/Refresh and review completion, save through {@link
 * AcceptedDocuments} inside their own transactions.
 *
 * <p>The file is read, hashed and validated once, outside any transaction. The transaction that
 * follows does only SQL, through {@link AcceptedDocuments}: it confirms the accepted hash is still
 * the one the read started from, so a concurrent change makes the import a {@link Result#CONFLICT}
 * that changes nothing, then stores the bytes with their hash, which is the copy's revision,
 * rebuilds the served copy and updates the title's metadata. A title with an open review batch is
 * never imported; its batch completion owns that import.
 */
@Service
public class DocumentImportService {
  private final CatalogueStore catalogue;
  private final DocumentStore documents;
  private final AcceptedDocuments acceptedDocuments;
  private final TransactionTemplate transactions;

  public DocumentImportService(
      CatalogueStore catalogue,
      DocumentStore documents,
      AcceptedDocuments acceptedDocuments,
      TransactionTemplate transactions) {
    this.catalogue = catalogue;
    this.documents = documents;
    this.acceptedDocuments = acceptedDocuments;
    this.transactions = transactions;
  }

  /**
   * Imports the title's registered file as its accepted copy.
   *
   * @throws NoSuchElementException if the title is unknown
   * @throws IOException if the file cannot be read or fails the same validation as any document
   *     read, including a document ID that differs from the index
   */
  public Result importFile(String titleId) throws IOException {
    CatalogueEntry entry =
        catalogue
            .find(titleId)
            .orElseThrow(() -> new NoSuchElementException("Unknown title: " + titleId));
    Optional<String> expected = acceptedDocuments.hash(titleId);
    var read = documents.readBytes(entry);
    if (expected.filter(read.sha256()::equals).isPresent()) return Result.UNCHANGED;
    return transactions.execute(
        status -> acceptedDocuments.saveAndRegister(entry, expected.orElse(null), read));
  }
}
