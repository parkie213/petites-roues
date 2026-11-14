package app.petitesroues.study;

import app.petitesroues.library.DocumentConflictException;
import app.petitesroues.library.DocumentImportService;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.CueChange;
import app.petitesroues.model.review.CueReviewRequest;
import app.petitesroues.model.review.ReviewCompletion;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serves each title's document, the accepted copy plus its queued player edits, and hands those
 * edits to agents in batches. The application never writes document files and never reads them to
 * answer a read: an agent claims a batch, writes the batch's document into the file with its
 * annotations, checkpoints it with Git and completes the batch, which imports that file as the new
 * accepted copy.
 *
 * <p>A read is a lookup of the title's served copy (see {@link ServedDocuments}) and its queue rows
 * in one transaction, so a document is wholly before or wholly after a completion. Queued edits
 * stay in the served copy until their batch is completed or discarded; edits that no longer apply
 * are skipped and reported as conflicts. Completing and discarding rebuild the served copy in the
 * transaction that changes the queue.
 */
@Service
public class ReviewQueueService {
  private static final Logger log = LoggerFactory.getLogger(ReviewQueueService.class);
  private static final Set<String> OUTCOMES =
      Set.of("applied", "already_correct", "not_adopted", "reviewed");

  private final CatalogueStore titles;
  private final DocumentStore documents;
  private final AcceptedDocumentStore accepted;
  private final DocumentImportService imports;
  private final ReviewQueue queue;
  private final ServedDocuments served;
  private final TransactionTemplate transactions;

  public ReviewQueueService(
      CatalogueStore titles,
      DocumentStore documents,
      AcceptedDocumentStore accepted,
      DocumentImportService imports,
      ReviewQueue queue,
      ServedDocuments served,
      TransactionTemplate transactions) {
    this.titles = titles;
    this.documents = documents;
    this.accepted = accepted;
    this.imports = imports;
    this.queue = queue;
    this.served = served;
    this.transactions = transactions;
  }

  /**
   * The served document and the state of every pending edit in it; {@code baseSha256} is the hash
   * of the accepted copy it was replayed over.
   */
  public record Effective(StudyDocument document, String baseSha256, List<CueChange> pending) {}

  /**
   * A claimed batch: its edits and the document as the accepted copy plus exactly those edits.
   * Later edits are left out; they keep being replayed over whatever copy the completion accepts.
   */
  public record BatchView(
      long batch, String baseSha256, List<CueChange> changes, StudyDocument document) {}

  /**
   * The served copy and the queue rows read in one transaction; a missing or stale served copy is
   * rebuilt first. Never reads the file and replays nothing otherwise.
   *
   * @throws DocumentConflictException if the title has no accepted copy yet
   */
  public Effective effective(CatalogueEntry title) {
    return transactions.execute(
        status -> {
          var copy = served.current(title);
          return new Effective(served.document(copy), copy.baseSha256(), queue.read(title));
        });
  }

  public StudyDocument document(CatalogueEntry title) {
    return transactions.execute(status -> served.document(served.current(title)));
  }

  /** The served document as canonical JSON bytes, exactly as stored. */
  public byte[] servedBytes(CatalogueEntry title) {
    return transactions.execute(status -> served.current(title).json());
  }

  public List<CueChange> pending(String id) throws IOException {
    return effective(entry(id)).pending();
  }

  public List<CueChange> history(String id) {
    return queue.history(entry(id));
  }

  public List<ReviewQueue.PendingTitle> pendingTitles() {
    return queue.pendingTitles();
  }

  /**
   * Queues a review request for each cue, all or none: every cue must exist in the served document
   * and every request needs text. The validation and the appends share one transaction. Returns how
   * many were queued.
   */
  public int requestReview(String id, List<CueReviewRequest> requests) {
    if (requests == null || requests.isEmpty())
      throw new IllegalArgumentException("List at least one cue to review");
    var edits =
        requests.stream()
            .map(
                r ->
                    new DocumentEdit.ReviewRequest(
                        r.cueId(), r.text() == null ? null : r.text().strip()))
            .toList();
    transactions.executeWithoutResult(
        status -> {
          var title = entry(id);
          var current = document(title);
          for (var edit : edits) DocumentEdits.apply(current, edit);
          edits.forEach(edit -> queue.append(title, edit));
        });
    return edits.size();
  }

  /**
   * Returns the title's open batch, or claims up to {@code limit} of the oldest pending edits as a
   * new one against the accepted copy. The batch document is built from the same snapshot: the
   * served copy when the batch covers every pending edit, otherwise the accepted copy with just the
   * batch's edits replayed. Empty when nothing is pending.
   */
  public Optional<BatchView> claim(String id, int limit) throws IOException {
    if (limit < 1) throw new IllegalArgumentException("Limit must be positive");
    return transactions.execute(
        status -> {
          var title = entry(id);
          var open = queue.openBatch(title);
          if (open.isEmpty() && queue.read(title).isEmpty()) return Optional.<BatchView>empty();
          var current = served.current(title);
          var batch = open.orElseGet(() -> queue.claim(title, current.baseSha256(), limit));
          var rows = queue.read(title);
          var changes = rows.stream().filter(c -> Objects.equals(c.batch(), batch.id())).toList();
          var document =
              changes.size() == rows.size()
                  ? served.document(current)
                  : ServedDocuments.replay(
                          acceptedCopy(title), rows, c -> Objects.equals(c.batch(), batch.id()))
                      .document();
          return Optional.of(new BatchView(batch.id(), batch.baseSha256(), changes, document));
        });
  }

  /**
   * Records decisions for every edit in an open batch and closes it, importing the registered file
   * as the title's accepted copy in the same transaction; either both happen or neither. The file
   * is read once, before the transaction. Replayable edits can only be completed after the file has
   * changed since the claim, unless they are explicitly {@code not_adopted}; this stops an agent
   * from completing edits it never wrote. Comments and review requests change nothing by
   * themselves, so they never require a file change. The served copy is rebuilt in the same
   * transaction, even when the file is unchanged, because the closed rows leave it; the edits
   * queued after the batch are replayed over the new accepted copy with fresh conflict states.
   *
   * <p>Repeating the completion of a closed batch succeeds when the file is the accepted copy, so a
   * lost response can be retried.
   *
   * @throws DocumentConflictException if the accepted copy is no longer the one the batch was
   *     claimed against
   */
  public void complete(String id, long batchId, ReviewCompletion request) throws IOException {
    String fallback = request == null || request.outcome() == null ? "applied" : request.outcome();
    var overrides =
        request == null || request.outcomes() == null ? Map.<Long, String>of() : request.outcomes();
    if (!OUTCOMES.contains(fallback) || !OUTCOMES.containsAll(overrides.values()))
      throw new IllegalArgumentException("Unsupported review outcome");
    var title = entry(id);
    var file = documents.readBytes(title);
    transactions.executeWithoutResult(
        status -> {
          var found = queue.batch(title, batchId);
          if (found.isPresent()
              && found.get().completedAt() != null
              && accepted.findHash(title.id()).filter(file.sha256()::equals).isPresent()) return;
          var batch =
              found
                  .filter(b -> b.completedAt() == null)
                  .orElseThrow(() -> new NoSuchElementException("No open batch " + batchId));
          var rows =
              queue.read(title).stream().filter(c -> Objects.equals(c.batch(), batchId)).toList();
          var sequences = rows.stream().map(CueChange::sequence).toList();
          if (!sequences.containsAll(overrides.keySet()))
            throw new IllegalArgumentException("Outcome for an edit outside this batch");
          var outcomes = new LinkedHashMap<Long, String>();
          for (var row : rows)
            outcomes.put(row.sequence(), overrides.getOrDefault(row.sequence(), fallback));
          boolean unwritten =
              rows.stream()
                  .anyMatch(
                      r ->
                          r.command() != null
                              && r.command().changesDocument()
                              && !"not_adopted".equals(outcomes.get(r.sequence())));
          if (unwritten && file.sha256().equals(batch.baseSha256()))
            throw new IllegalArgumentException(
                "document.json is unchanged since this batch was claimed. Write the batch's"
                    + " edits into it first, or complete them as not_adopted.");
          if (imports.acceptCompletedBatch(title, batch.baseSha256(), file)
              == DocumentImportService.Result.CONFLICT)
            throw new DocumentConflictException(
                "The accepted copy of " + id + " changed since batch " + batchId + " was claimed");
          queue.complete(title, batchId, outcomes, Instant.now().toString());
          served.rebuild(title);
        });
  }

  /**
   * Imports the title's registered file as its accepted copy; see {@link
   * DocumentImportService#importFile}.
   *
   * @throws DocumentConflictException if the title has an open batch or the accepted copy changed
   *     during the import
   */
  public DocumentImportService.Result importFile(String id) throws IOException {
    var result = imports.importFile(id);
    return switch (result) {
      case SKIPPED_OPEN_BATCH ->
          throw new DocumentConflictException(
              "Title "
                  + id
                  + " has an open review batch: complete or discard batch "
                  + queue.openBatch(entry(id)).map(ReviewQueue.Batch::id).orElse(null)
                  + " first");
      case CONFLICT ->
          throw new DocumentConflictException(
              "The accepted copy of " + id + " changed during the import; try again");
      default -> result;
    };
  }

  /**
   * Discards exactly the displayed pending edits, reverting them in the served document, which is
   * rebuilt in the same transaction. Completed decisions are kept.
   */
  public void discard(String id, List<Long> expected) {
    transactions.executeWithoutResult(
        status -> {
          var title = entry(id);
          if (!queue.read(title).stream().map(CueChange::sequence).toList().equals(expected))
            throw new IllegalArgumentException("Queue changed; reload before discarding");
          queue.discard(title);
          served.rebuild(title);
        });
  }

  /**
   * Rebuilds the served copy of every title whose copy is missing or stale or that has pending
   * edits, one transaction per title. A title that cannot be rebuilt is logged and skipped; it is
   * rebuilt on its next read. Safe to repeat.
   *
   * @return how many titles were rebuilt
   */
  public int rebuildServedCopies() {
    int rebuilt = 0;
    for (String id : accepted.titlesNeedingServed()) {
      try {
        transactions.executeWithoutResult(status -> served.rebuild(entry(id)));
        rebuilt++;
      } catch (RuntimeException failure) {
        log.warn("Cannot rebuild the served copy of title {}: {}", id, failure.toString());
      }
    }
    return rebuilt;
  }

  /** Loads and decodes the accepted copy, for a claim that covers only some pending edits. */
  private StudyDocument acceptedCopy(CatalogueEntry title) {
    return accepted
        .find(title.id())
        .map(copy -> documents.decode(copy.json()))
        .orElseThrow(
            () ->
                new DocumentConflictException(
                    "Title "
                        + title.id()
                        + " has no accepted copy of its document; Refresh its folder"));
  }

  private CatalogueEntry entry(String id) {
    return titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
  }
}
