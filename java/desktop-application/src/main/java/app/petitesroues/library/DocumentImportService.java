package app.petitesroues.library;

import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps each title's accepted copy (see {@link AcceptedDocumentStore}) in step with its registered
 * document file. A file enters the accepted copy only through this service, by an explicit import,
 * by Add/Refresh (through {@link #accept}) or by the startup backfill.
 *
 * <p>The file is read, hashed and validated once, outside any transaction. The transaction that
 * follows does only SQL: it confirms the accepted hash is still the one the read started from, so a
 * concurrent change makes the import a {@link Result#CONFLICT} that changes nothing, then stores
 * the bytes with their hash, which is the copy's revision. A title with an open review batch is
 * never imported; its batch completion owns that import.
 */
@Service
public class DocumentImportService {
  private static final Logger log = LoggerFactory.getLogger(DocumentImportService.class);

  /** What an import did to the title's accepted copy. */
  public enum Result {
    /** The copy now holds the file's bytes, and the title's metadata follows the document. */
    IMPORTED,
    /** The accepted copy already has the file's hash; nothing changed. */
    UNCHANGED,
    /** The title has an open review batch; nothing changed. */
    SKIPPED_OPEN_BATCH,
    /** The accepted copy changed while the file was being read; nothing changed. */
    CONFLICT
  }

  private final CatalogueStore catalogue;
  private final DocumentStore documents;
  private final AcceptedDocumentStore accepted;
  private final ReviewQueue queue;
  private final RepositoryContentService registration;
  private final TransactionTemplate transactions;

  public DocumentImportService(
      CatalogueStore catalogue,
      DocumentStore documents,
      AcceptedDocumentStore accepted,
      ReviewQueue queue,
      RepositoryContentService registration,
      TransactionTemplate transactions) {
    this.catalogue = catalogue;
    this.documents = documents;
    this.accepted = accepted;
    this.queue = queue;
    this.registration = registration;
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
    Optional<String> expected = accepted.findHash(titleId);
    var read = documents.readBytes(entry);
    if (expected.filter(read.sha256()::equals).isPresent()) return Result.UNCHANGED;
    return transactions.execute(
        status -> {
          Result result = accept(entry, expected.orElse(null), read);
          if (result == Result.IMPORTED) registration.updateMetadata(entry, read.document());
          return result;
        });
  }

  /**
   * Saves {@code read} as the title's accepted copy inside the caller's transaction, unless the
   * accepted hash is no longer {@code expectedHash} (null for none), already equals the file's, or
   * the title has an open review batch. Performs no file access.
   */
  Result accept(CatalogueEntry entry, String expectedHash, DocumentStore.Bytes read) {
    Optional<String> current = accepted.findHash(entry.id());
    if (!Objects.equals(current.orElse(null), expectedHash)) return Result.CONFLICT;
    if (current.filter(read.sha256()::equals).isPresent()) return Result.UNCHANGED;
    if (queue.hasOpenBatch(entry.id())) return Result.SKIPPED_OPEN_BATCH;
    accepted.save(entry.id(), read.bytes(), read.sha256(), Instant.now().toString());
    return Result.IMPORTED;
  }

  /**
   * The import that completes a review batch, inside the caller's transaction: saves {@code read}
   * as the accepted copy and updates the title's metadata, even though the title has its open
   * batch. {@code batchBaseHash} is the accepted hash the batch was claimed against; if the copy
   * has moved the result is {@link Result#CONFLICT}. {@link Result#UNCHANGED} when the file already
   * is the accepted copy. Performs no file access.
   */
  public Result acceptCompletedBatch(
      CatalogueEntry entry, String batchBaseHash, DocumentStore.Bytes read) {
    Optional<String> current = accepted.findHash(entry.id());
    if (!current.filter(batchBaseHash::equals).isPresent()) return Result.CONFLICT;
    if (current.filter(read.sha256()::equals).isPresent()) return Result.UNCHANGED;
    accepted.save(entry.id(), read.bytes(), read.sha256(), Instant.now().toString());
    registration.updateMetadata(entry, read.document());
    return Result.IMPORTED;
  }

  /** Accepted hashes of a repository's titles by document ID, for Refresh's conflict check. */
  Map<String, String> acceptedHashes(String repositoryId) {
    return accepted.hashesByDocumentId(repositoryId);
  }

  /**
   * Imports every title that has no accepted copy. A title whose file is missing or invalid is
   * logged and left without one; it does not stop the others. Safe to repeat: titles already
   * imported are not selected again.
   *
   * @return how many titles gained an accepted copy
   */
  public int backfill() {
    int imported = 0;
    List<String> titles = accepted.titlesWithoutCopy();
    for (String titleId : titles) {
      try {
        if (importFile(titleId) == Result.IMPORTED) imported++;
      } catch (IOException | RuntimeException failure) {
        log.warn("Cannot accept the document of title {}: {}", titleId, failure.toString());
      }
    }
    return imported;
  }
}
