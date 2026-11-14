package app.petitesroues.library;

import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ServedDocuments;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The rules for making a document file's bytes a title's accepted copy (see {@link
 * AcceptedDocumentStore}). Every method runs inside the caller's transaction and does database work
 * only: it never reads a file and never opens or commits a transaction, so the caller decides which
 * operations succeed or fail together.
 *
 * <p>Whoever saves must name the accepted hash it started from. A copy that has moved since makes
 * the save a {@link Result#CONFLICT}; a file that already is the accepted copy is {@link
 * Result#UNCHANGED}; a title with an open review batch is {@link Result#SKIPPED_OPEN_BATCH}, except
 * for that batch's own completion. In each of those cases nothing changes. The checks run in that
 * order.
 */
@Component
public class AcceptedDocuments {
  /** What a save did to the title's accepted copy. */
  public enum Result {
    /** The copy now holds the file's bytes. */
    IMPORTED,
    /** The accepted copy already has the file's hash; nothing changed. */
    UNCHANGED,
    /** The title has an open review batch; nothing changed. */
    SKIPPED_OPEN_BATCH,
    /** The accepted copy was not the one the caller started from; nothing changed. */
    CONFLICT
  }

  private final AcceptedDocumentStore accepted;
  private final ReviewQueue queue;
  private final ServedDocuments served;
  private final RepositoryContentService registration;

  public AcceptedDocuments(
      AcceptedDocumentStore accepted,
      ReviewQueue queue,
      ServedDocuments served,
      RepositoryContentService registration) {
    this.accepted = accepted;
    this.queue = queue;
    this.served = served;
    this.registration = registration;
  }

  /** The title's accepted hash, empty when it has no accepted copy. */
  public Optional<String> hash(String titleId) {
    return accepted.findHash(titleId);
  }

  /** Accepted hashes of a repository's titles by document ID, for Refresh's conflict check. */
  Map<String, String> acceptedHashes(String repositoryId) {
    return accepted.hashesByDocumentId(repositoryId);
  }

  /**
   * Saves {@code read} as the title's accepted copy unless the accepted hash is no longer {@code
   * expectedHash} (null for none), already equals the file's, or the title has an open review
   * batch. An {@link Result#IMPORTED} copy is followed by a rebuild of the served copy. The title's
   * metadata is left to the caller.
   */
  Result save(CatalogueEntry entry, String expectedHash, DocumentStore.Bytes read) {
    Result result = store(entry, expectedHash, read, false);
    if (result == Result.IMPORTED) served.rebuild(entry);
    return result;
  }

  /**
   * {@link #save} for an explicit import: an {@link Result#IMPORTED} copy is also followed by an
   * update of the title's metadata from the document.
   */
  public Result saveAndRegister(
      CatalogueEntry entry, String expectedHash, DocumentStore.Bytes read) {
    Result result = save(entry, expectedHash, read);
    if (result == Result.IMPORTED) registration.updateMetadata(entry, read.document());
    return result;
  }

  /**
   * The save that completes a review batch: saves {@code read} as the accepted copy and updates the
   * title's metadata even though the title has its open batch. {@code batchBaseHash} is the
   * accepted hash the batch was claimed against; if the copy has moved the result is {@link
   * Result#CONFLICT}. {@link Result#UNCHANGED} when the file already is the accepted copy. Does not
   * rebuild the served copy: the completion does, after closing the batch's rows.
   */
  public Result saveCompletedBatch(
      CatalogueEntry entry, String batchBaseHash, DocumentStore.Bytes read) {
    Result result = store(entry, batchBaseHash, read, true);
    if (result == Result.IMPORTED) registration.updateMetadata(entry, read.document());
    return result;
  }

  private Result store(
      CatalogueEntry entry, String expectedHash, DocumentStore.Bytes read, boolean allowOpenBatch) {
    Optional<String> current = accepted.findHash(entry.id());
    if (!Objects.equals(current.orElse(null), expectedHash)) return Result.CONFLICT;
    if (current.filter(read.sha256()::equals).isPresent()) return Result.UNCHANGED;
    if (!allowOpenBatch && queue.hasOpenBatch(entry.id())) return Result.SKIPPED_OPEN_BATCH;
    accepted.save(entry.id(), read.bytes(), read.sha256(), Instant.now().toString());
    return Result.IMPORTED;
  }
}
