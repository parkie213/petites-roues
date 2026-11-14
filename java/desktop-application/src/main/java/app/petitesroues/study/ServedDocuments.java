package app.petitesroues.study;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.DocumentConflictException;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.CueChange;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Owns each title's served copy: the document as the player sees it, which is the accepted copy
 * with the pending queue replayed over it, stored as canonical JSON (see {@link
 * AcceptedDocumentStore}) so that a read is a lookup, not a replay.
 *
 * <p>Every method that touches the database runs in the caller's transaction and does SQL only,
 * never file access. Every transaction that changes a title's accepted copy or its pending rows
 * must leave this invariant true at commit: the served bytes equal the encoded replay of the
 * pending rows over the accepted copy, the stamp equals the accepted hash, and each pending row's
 * stored conflict flag equals that replay's verdict. {@link #rebuild} is the only place that
 * replays to write, so it is the only place that sets conflict flags; an edit appended through
 * {@link #save} cannot conflict because it was validated against the copy it extends. Anything
 * missing or stale is rebuilt by {@link #current} when it is next read, so a lost row only costs
 * one replay.
 */
@Component
public class ServedDocuments {
  private final AcceptedDocumentStore accepted;
  private final ReviewQueue queue;
  private final DocumentStore documents;
  private final StudyDocumentCodec codec;

  public ServedDocuments(
      AcceptedDocumentStore accepted,
      ReviewQueue queue,
      DocumentStore documents,
      StudyDocumentCodec codec) {
    this.accepted = accepted;
    this.queue = queue;
    this.documents = documents;
    this.codec = codec;
  }

  /** The served bytes and the hash of the accepted copy they were replayed over. */
  public record Copy(byte[] json, String baseSha256) {}

  /** The result of {@link #replay}: the document and every row with its state. */
  public record Replay(StudyDocument document, List<CueChange> rows) {}

  /**
   * The title's served copy. A missing row, or one stamped with a different accepted hash, is
   * rebuilt first, so the caller's transaction sees a copy consistent with the accepted copy.
   *
   * @throws DocumentConflictException if the title has no accepted copy yet
   */
  public Copy current(CatalogueEntry title) {
    String hash = acceptedHash(title);
    return accepted
        .findServed(title.id(), hash)
        .map(json -> new Copy(json, hash))
        .orElseGet(() -> rebuild(title));
  }

  /** Decodes a served copy. */
  public StudyDocument document(Copy copy) {
    return documents.decode(copy.json());
  }

  /** The canonical bytes of a document, as stored and as the phone receives them. */
  public byte[] encode(StudyDocument document) {
    return codec.encode(document).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Stores the edited document as the served copy over the current accepted copy. For the edit path
   * only: the caller has just validated the edit against {@link #current} and appends its queue row
   * in the same transaction.
   */
  public void save(CatalogueEntry title, byte[] json) {
    accepted.saveServed(title.id(), acceptedHash(title), json);
  }

  /**
   * Replays the title's pending rows over its accepted copy, stores the result as the served copy
   * and updates the conflict flag of every row whose verdict changed. Returns the stored copy.
   *
   * @throws DocumentConflictException if the title has no accepted copy yet
   */
  public Copy rebuild(CatalogueEntry title) {
    var copy = accepted.find(title.id()).orElseThrow(() -> noAcceptedCopy(title));
    var rows = queue.read(title);
    var replay = replay(documents.decode(copy.json()), rows, row -> true);
    byte[] json = encode(replay.document());
    accepted.saveServed(title.id(), copy.sha256(), json);
    for (int i = 0; i < rows.size(); i++) {
      boolean was = "conflict".equals(rows.get(i).state());
      boolean now = "conflict".equals(replay.rows().get(i).state());
      if (was != now) queue.setConflict(rows.get(i).sequence(), now);
    }
    return new Copy(json, copy.sha256());
  }

  /**
   * Replays the included pending rows over {@code document} in order, without touching the
   * database. A row that no longer applies is skipped and marked {@code conflict}; the others are
   * {@code pending}, or {@code review} for rows queued before edits were replayed. Rows not
   * included are {@code pending} and not applied.
   */
  public static Replay replay(
      StudyDocument document, List<CueChange> rows, Predicate<CueChange> include) {
    var states = new ArrayList<CueChange>();
    for (var row : rows) {
      String state;
      if (row.command() == null) state = "review";
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
    return new Replay(document, List.copyOf(states));
  }

  private String acceptedHash(CatalogueEntry title) {
    return accepted.findHash(title.id()).orElseThrow(() -> noAcceptedCopy(title));
  }

  private static DocumentConflictException noAcceptedCopy(CatalogueEntry title) {
    return new DocumentConflictException(
        "Title " + title.id() + " has no accepted copy of its document; Refresh its folder");
  }
}
