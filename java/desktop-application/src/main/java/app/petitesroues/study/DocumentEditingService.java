package app.petitesroues.study;

import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.ComicBoxCreate;
import app.petitesroues.model.editing.ComicBoxUpdate;
import app.petitesroues.model.editing.ComicTextEdit;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.editing.MergeCues;
import app.petitesroues.model.editing.SplitCue;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.model.editing.VideoCueEdit;
import app.petitesroues.model.library.CatalogueEntry;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accepts player and reader edits by queueing them; it never writes document files. Each edit is
 * applied to the served document (accepted copy plus earlier queued edits) to validate it, then
 * stored with any identities it introduces. Edits that change nothing are not queued.
 *
 * <p>Loading the served document, validating the edit and appending the queue row happen in one
 * transaction. This relies on the application's database having a single pooled connection ({@code
 * spring.datasource.hikari.maximum-pool-size=1}), so a transaction excludes all other database
 * work: each edit is validated against a state that includes every edit committed before it, and a
 * repository removal or batch completion lands wholly before or after it. Identities for added cues
 * and boxes are generated once per call, before the transaction.
 */
@Service
public class DocumentEditingService {
  private final CatalogueStore titles;
  private final ReviewQueueService served;
  private final ReviewQueue queue;
  private final TransactionTemplate transactions;

  public DocumentEditingService(
      CatalogueStore titles,
      ReviewQueueService served,
      ReviewQueue queue,
      TransactionTemplate transactions) {
    this.titles = titles;
    this.served = served;
    this.queue = queue;
    this.transactions = transactions;
  }

  public StudyDocument suggestCorrection(String id, String cueId, String suggestion)
      throws IOException {
    return record(
        id, new DocumentEdit.Suggestion(cueId, suggestion == null ? null : suggestion.trim()));
  }

  /** Queues feedback on a cue for the reviewing agent without changing the document. */
  public StudyDocument comment(String id, String cueId, String text) throws IOException {
    var edit = new DocumentEdit.Comment(cueId, text == null ? null : text.strip());
    return transactions.execute(
        status -> {
          var title = entry(id);
          var current = served.document(title);
          DocumentEdits.apply(current, edit);
          queue.append(title, edit);
          return current;
        });
  }

  /** Validates the entire timing batch before applying it; returns the total cue count. */
  public int saveCueTiming(String id, List<UpdateCueTiming> edits) throws IOException {
    return record(id, new DocumentEdit.Timing(edits == null ? null : List.copyOf(edits)))
        .video()
        .cues()
        .size();
  }

  public StudyDocument editCue(String id, String cueId, VideoCueEdit edit) throws IOException {
    return record(id, new DocumentEdit.EditVideoCue(cueId, edit));
  }

  public StudyDocument addCue(String id, VideoCueEdit edit) throws IOException {
    return record(id, new DocumentEdit.AddVideoCue(UUID.randomUUID().toString(), edit));
  }

  public StudyDocument splitCue(String id, String cueId, SplitCue split) throws IOException {
    return record(id, new DocumentEdit.SplitVideoCue(cueId, UUID.randomUUID().toString(), split));
  }

  /** Records the selection in playback order, whatever order the player selected it in. */
  public StudyDocument mergeCues(String id, MergeCues merge) throws IOException {
    if (merge == null || merge.ids() == null)
      throw new IllegalArgumentException("Select at least two distinct consecutive cues");
    var selected = new HashSet<>(merge.ids());
    return transactions.execute(
        status -> {
          var ordered = new ArrayList<String>();
          for (var cue : served.document(entry(id)).video().cues())
            if (selected.contains(cue.line().id())) ordered.add(cue.line().id());
          if (ordered.size() != merge.ids().size()) ordered = new ArrayList<>(merge.ids());
          return record(id, new DocumentEdit.MergeVideoCues(List.copyOf(ordered)));
        });
  }

  public StudyDocument deleteCue(String id, String cueId) throws IOException {
    return record(id, new DocumentEdit.DeleteVideoCue(cueId));
  }

  public StudyDocument editComicText(String id, String lineId, ComicTextEdit edit)
      throws IOException {
    return record(id, new DocumentEdit.EditComicText(lineId, edit));
  }

  /** Adds a box with fresh UUIDs for the box and the one line it starts with. */
  public StudyDocument createComicBox(String id, ComicBoxCreate create) throws IOException {
    return record(
        id,
        new DocumentEdit.CreateComicBox(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), create));
  }

  public StudyDocument placeComicBox(String id, String boxId, ComicBoxUpdate update)
      throws IOException {
    return record(id, new DocumentEdit.PlaceComicBox(boxId, update));
  }

  public StudyDocument deleteComicBox(String id, String boxId) throws IOException {
    return record(id, new DocumentEdit.DeleteComicBox(boxId));
  }

  /**
   * Applies the edit to the served document and queues it unless it changes nothing, in one
   * transaction (joining the caller's).
   */
  private StudyDocument record(String id, DocumentEdit edit) {
    return transactions.execute(
        status -> {
          var title = entry(id);
          var current = served.document(title);
          var result = DocumentEdits.apply(current, edit);
          if (!result.equals(current)) queue.append(title, edit);
          return result;
        });
  }

  private CatalogueEntry entry(String id) {
    return titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
  }
}
