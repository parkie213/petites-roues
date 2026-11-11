package app.petitesroues.study;

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
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serves documents as the file plus queued player edits, and hands those edits to agents in
 * batches. The application never writes document files: an agent claims a batch, writes the batch's
 * document into the file with its annotations, checkpoints it with Git and completes the batch.
 *
 * <p>A batch remembers the SHA-256 of the file it was claimed against. Its edits are replayed only
 * while the file still has that hash, so once the agent rewrites the file they are not applied a
 * second time. Edits that no longer apply to the file are skipped and reported as conflicts.
 */
@Service
public class ReviewQueueService {
  private static final Set<String> OUTCOMES =
      Set.of("applied", "already_correct", "not_adopted", "reviewed");

  private final CatalogueStore titles;
  private final DocumentStore documents;
  private final ReviewQueue queue;
  private final TransactionTemplate transactions;

  public ReviewQueueService(
      CatalogueStore titles,
      DocumentStore documents,
      ReviewQueue queue,
      TransactionTemplate transactions) {
    this.titles = titles;
    this.documents = documents;
    this.queue = queue;
    this.transactions = transactions;
  }

  /** The served document and the state of every pending edit in it. */
  public record Effective(StudyDocument document, String baseSha256, List<CueChange> pending) {}

  /**
   * A claimed batch: its edits and the document as the file plus exactly those edits. Later edits
   * are left out; they keep being replayed over whatever the agent writes.
   */
  public record BatchView(
      long batch, String baseSha256, List<CueChange> changes, StudyDocument document) {}

  public Effective effective(CatalogueEntry title) throws IOException {
    return replay(title, documents.read(title), queue.read(title), change -> true);
  }

  public StudyDocument document(CatalogueEntry title) throws IOException {
    return effective(title).document();
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
   * and every request needs text. Returns how many were queued.
   */
  public int requestReview(String id, List<CueReviewRequest> requests) throws IOException {
    if (requests == null || requests.isEmpty())
      throw new IllegalArgumentException("List at least one cue to review");
    var title = entry(id);
    var current = document(title);
    var edits =
        requests.stream()
            .map(
                r ->
                    new DocumentEdit.ReviewRequest(
                        r.cueId(), r.text() == null ? null : r.text().strip()))
            .toList();
    for (var edit : edits) DocumentEdits.apply(current, edit);
    transactions.executeWithoutResult(status -> edits.forEach(edit -> queue.append(title, edit)));
    return edits.size();
  }

  /**
   * Returns the title's open batch, or claims up to {@code limit} of the oldest pending edits as a
   * new one. Empty when nothing is pending.
   */
  public Optional<BatchView> claim(String id, int limit) throws IOException {
    if (limit < 1) throw new IllegalArgumentException("Limit must be positive");
    var title = entry(id);
    var snapshot = documents.read(title);
    var batch =
        transactions.execute(
            status -> {
              var open = queue.openBatch(title);
              if (open.isPresent()) return open;
              if (queue.read(title).isEmpty()) return Optional.<ReviewQueue.Batch>empty();
              return Optional.of(queue.claim(title, snapshot.sha256(), limit));
            });
    if (batch.isEmpty()) return Optional.empty();
    long batchId = batch.get().id();
    var rows = queue.read(title);
    var effective =
        replay(title, snapshot, rows, change -> Objects.equals(change.batch(), batchId));
    var changes =
        effective.pending().stream().filter(c -> Objects.equals(c.batch(), batchId)).toList();
    return Optional.of(
        new BatchView(batchId, batch.get().baseSha256(), changes, effective.document()));
  }

  /**
   * Records decisions for every edit in an open batch and closes it. Replayable edits can only be
   * completed after the file has changed since the claim, unless they are explicitly {@code
   * not_adopted}; this stops an agent from completing edits it never wrote. Comments and review
   * requests change nothing by themselves, so they never require a file change.
   */
  public void complete(String id, long batchId, ReviewCompletion request) throws IOException {
    String fallback = request == null || request.outcome() == null ? "applied" : request.outcome();
    var overrides =
        request == null || request.outcomes() == null ? Map.<Long, String>of() : request.outcomes();
    if (!OUTCOMES.contains(fallback) || !OUTCOMES.containsAll(overrides.values()))
      throw new IllegalArgumentException("Unsupported review outcome");
    var title = entry(id);
    var snapshot = documents.read(title);
    transactions.executeWithoutResult(
        status -> {
          var batch =
              queue
                  .batch(title, batchId)
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
          if (unwritten && snapshot.sha256().equals(batch.baseSha256()))
            throw new IllegalArgumentException(
                "document.json is unchanged since this batch was claimed. Write the batch's"
                    + " edits into it first, or complete them as not_adopted.");
          queue.complete(title, batchId, outcomes, Instant.now().toString());
        });
  }

  /**
   * Discards exactly the displayed pending edits, reverting them in the served document. Completed
   * decisions are kept.
   */
  public void discard(String id, List<Long> expected) {
    transactions.executeWithoutResult(
        status -> {
          var title = entry(id);
          if (!queue.read(title).stream().map(CueChange::sequence).toList().equals(expected))
            throw new IllegalArgumentException("Queue changed; reload before discarding");
          queue.discard(title);
        });
  }

  /** Replays the selected pending edits over the file, recording each edit's state. */
  private Effective replay(
      CatalogueEntry title,
      DocumentStore.Snapshot snapshot,
      List<CueChange> rows,
      Predicate<CueChange> include) {
    var bases = new HashMap<Long, String>();
    var document = snapshot.document();
    var states = new ArrayList<CueChange>();
    for (var row : rows) {
      String state;
      if (row.command() == null) state = "review";
      else if (row.batch() != null
          && !bases
              .computeIfAbsent(row.batch(), b -> queue.batch(title, b).orElseThrow().baseSha256())
              .equals(snapshot.sha256())) state = "written";
      else {
        state = "pending";
        if (include.test(row)) {
          try {
            document = DocumentEdits.apply(document, row.command());
          } catch (IllegalArgumentException | NoSuchElementException noLongerApplies) {
            state = "conflict";
          }
        }
      }
      states.add(row.withState(state));
    }
    return new Effective(document, snapshot.sha256(), List.copyOf(states));
  }

  private CatalogueEntry entry(String id) {
    return titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
  }
}
