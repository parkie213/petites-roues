package app.petitesroues.study;

import app.petitesroues.library.LibraryUpdateService;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.CommandAcceptance;
import app.petitesroues.model.editing.CommandRequest;
import app.petitesroues.model.editing.CommandResult;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.editing.EditCommand;
import app.petitesroues.model.library.CatalogueEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Accepts typed command batches with one validation, persistence and retry contract. The existing
 * update monitor is acquired before the transaction; portable files are never written here.
 */
@Service
public class DocumentCommandService {
  private final LibraryUpdateService monitor;
  private final CatalogueStore titles;
  private final ReviewQueueService served;
  private final ReviewQueue queue;
  private final CommandReceiptStore receipts;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final DocumentStore documents;

  public DocumentCommandService(
      LibraryUpdateService monitor,
      CatalogueStore titles,
      ReviewQueueService served,
      ReviewQueue queue,
      CommandReceiptStore receipts,
      TransactionTemplate transactions,
      ObjectMapper json,
      DocumentStore documents) {
    this.monitor = monitor;
    this.titles = titles;
    this.served = served;
    this.queue = queue;
    this.receipts = receipts;
    this.transactions = transactions;
    this.json = json;
    this.documents = documents;
  }

  /**
   * A document and opaque token for its exact file/queue snapshot, including feedback-only changes.
   */
  public CommandResult read(String id) throws IOException {
    synchronized (monitor) {
      return snapshot(entry(id));
    }
  }

  /**
   * Commits all commands and their receipt, or none; identical retries retain the original
   * acceptance without reapplying commands.
   */
  public CommandResult execute(String id, CommandRequest request) throws IOException {
    if (request == null
        || request.requestId() == null
        || request.requestId().isBlank()
        || request.requestId().length() > 200
        || request.expectedVersion() == null
        || request.commands() == null
        || request.commands().isEmpty()
        || request.commands().size() > 1000)
      throw new IllegalArgumentException("Supply requestId, expectedVersion and 1–1000 commands");
    synchronized (monitor) {
      var title = entry(id);
      var source = documents.read(title);
      return transactions.execute(
          status -> {
            String payload = json.writeValueAsString(request);
            var previous = receipts.find(id, request.requestId());
            if (previous.isPresent()) {
              if (!previous.get().request().equals(payload))
                throw new StateConflictException("Request ID already used for a different command");
              var accepted = json.readValue(previous.get().result(), CommandAcceptance.class);
              var current = describe(served.effective(title, source));
              return new CommandResult(
                  current.document(), current.version(), accepted.createdIds(), accepted.version());
            }
            var before = describe(served.effective(title, source));
            if (!before.version().equals(request.expectedVersion()))
              throw new StateConflictException("Document changed; reload before editing");
            var current = before.document();
            var created = new ArrayList<String>();
            for (var input : request.commands()) {
              if (input == null) throw new IllegalArgumentException("Command is required");
              var edit = materialize(input, current, created);
              var next = DocumentEdits.apply(current, edit);
              if (!next.equals(current) || !edit.changesDocument()) queue.append(title, edit);
              current = next;
            }
            var after = describe(served.effective(title, source));
            var result =
                new CommandResult(
                    after.document(), after.version(), List.copyOf(created), after.version());
            receipts.insert(
                id,
                request.requestId(),
                payload,
                json.writeValueAsString(
                    new CommandAcceptance(result.acceptedVersion(), result.createdIds())));
            return result;
          });
    }
  }

  private CatalogueEntry entry(String id) {
    return titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
  }

  private CommandResult snapshot(CatalogueEntry title) throws IOException {
    return describe(served.effective(title));
  }

  /** Describes an already captured effective state without rereading files or the queue. */
  CommandResult describe(ReviewQueueService.Effective effective) {
    try {
      var bytes =
          (effective.baseSha256() + json.writeValueAsString(effective.pending()))
              .getBytes(StandardCharsets.UTF_8);
      var version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      return new CommandResult(effective.document(), version, List.of(), version);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String allocate(List<String> created) {
    var id = UUID.randomUUID().toString();
    created.add(id);
    return id;
  }

  private static DocumentEdit materialize(
      EditCommand command, StudyDocument current, List<String> created) {
    return switch (command) {
      case EditCommand.Timing c -> new DocumentEdit.Timing(c.edits());
      case EditCommand.Suggestion c -> new DocumentEdit.Suggestion(c.cueId(), strip(c.text()));
      case EditCommand.Comment c -> new DocumentEdit.Comment(c.cueId(), strip(c.text()));
      case EditCommand.ReviewRequest c ->
          new DocumentEdit.ReviewRequest(c.cueId(), strip(c.text()));
      case EditCommand.EditVideoCue c -> new DocumentEdit.EditVideoCue(c.cueId(), c.edit());
      case EditCommand.AddVideoCue c -> new DocumentEdit.AddVideoCue(allocate(created), c.edit());
      case EditCommand.SplitVideoCue c ->
          new DocumentEdit.SplitVideoCue(c.cueId(), allocate(created), c.split());
      case EditCommand.MergeVideoCues c -> {
        if (c.ids() == null) throw new IllegalArgumentException("Select consecutive cues");
        var selected = new HashSet<>(c.ids());
        var ordered =
            current.video().cues().stream()
                .map(cue -> cue.line().id())
                .filter(selected::contains)
                .toList();
        yield new DocumentEdit.MergeVideoCues(ordered.size() == c.ids().size() ? ordered : c.ids());
      }
      case EditCommand.DeleteVideoCue c -> new DocumentEdit.DeleteVideoCue(c.cueId());
      case EditCommand.EditComicText c -> new DocumentEdit.EditComicText(c.lineId(), c.edit());
      case EditCommand.CreateComicBox c ->
          new DocumentEdit.CreateComicBox(allocate(created), allocate(created), c.create());
      case EditCommand.PlaceComicBox c -> new DocumentEdit.PlaceComicBox(c.boxId(), c.update());
      case EditCommand.DeleteComicBox c -> new DocumentEdit.DeleteComicBox(c.boxId());
    };
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}
