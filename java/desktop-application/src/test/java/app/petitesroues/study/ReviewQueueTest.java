package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.TestDocuments.catalogueEntry;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.DocumentConflictException;
import app.petitesroues.library.DocumentImportService;
import app.petitesroues.library.RepositoryContentService;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.ReviewCompletion;
import app.petitesroues.support.DatabaseFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ReviewQueueTest {
  @TempDir Path root;
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;
  ReviewQueue queue;
  ReviewQueueService service;
  DocumentImportService imports;
  AcceptedDocumentStore accepted;
  ServedDocuments servedDocuments;
  TransactionTemplate transactions;
  CatalogueEntry title;
  StudyDocumentCodec codec;
  final DocumentEdit delete = new DocumentEdit.DeleteVideoCue("stable-cue");

  @BeforeEach
  void setup() throws Exception {
    var mapper = StudyDocumentJson.newMapper();
    codec = new StudyDocumentCodec();
    source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    jdbc = new JdbcTemplate(source);
    DatabaseFixtures.migrate(source);
    queue = new ReviewQueue(jdbc, mapper);
    var catalogue = new CatalogueStore(jdbc);
    var store = new DocumentStore(new StudyDocumentReader(codec));
    accepted = new AcceptedDocumentStore(jdbc);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    servedDocuments = new ServedDocuments(accepted, queue, store, codec);
    imports =
        new DocumentImportService(
            catalogue,
            store,
            accepted,
            queue,
            servedDocuments,
            new RepositoryContentService(new ContentRepositoryStore(jdbc), catalogue),
            transactions);
    service =
        new ReviewQueueService(
            catalogue, store, accepted, imports, queue, servedDocuments, transactions);
    title = register("example");
    append(title, delete);
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  /**
   * Queues an edit the way the editing service does, then restores the served copy: the queue and
   * the served copy only change together.
   */
  private void append(CatalogueEntry entry, DocumentEdit edit) {
    transactions.executeWithoutResult(
        status -> {
          queue.append(entry, edit);
          servedDocuments.rebuild(entry);
        });
  }

  private CatalogueEntry register(String id) throws Exception {
    var entry = catalogueEntry(jdbc, id, root);
    new CatalogueStore(jdbc).saveTitle(entry);
    write(entry, document(id));
    imports.importFile(entry.id());
    return entry;
  }

  private void write(CatalogueEntry entry, StudyDocument document) throws Exception {
    Files.createDirectories(entry.documentFile().getParent());
    Files.write(entry.documentFile(), codec.encode(document).getBytes(StandardCharsets.UTF_8));
  }

  /** Claims the pending edits and writes their document, as an agent would. */
  private long claimAndWrite() throws Exception {
    var batch = service.claim(title.id(), 50).orElseThrow();
    write(title, batch.document());
    return batch.batch();
  }

  @Test
  void commandsRoundTripThroughTheQueue() {
    var stored = queue.read(title).getFirst();
    assertEquals(delete, stored.command());
    assertEquals("delete", stored.kind());
    assertEquals(List.of("stable-cue"), stored.cueIds());
    assertNull(stored.batch());
  }

  @Test
  void completedItemsLeavePendingQueueButRetainDecisionAndTaskDetails() throws Exception {
    var original = queue.read(title).getFirst();
    service.complete(title.id(), claimAndWrite(), new ReviewCompletion("applied", null));
    assertTrue(queue.read(title).isEmpty());
    var saved = queue.history(title).getFirst();
    assertEquals(original.sequence(), saved.sequence());
    assertEquals(original.timestamp(), saved.timestamp());
    assertEquals(original.cueIds(), saved.cueIds());
    assertNotNull(saved.reviewedAt());
    assertEquals("applied", saved.outcome());
  }

  @Test
  void aBatchClaimsOnlyUpToItsLimitAndLaterEditsStayUnclaimed() throws Exception {
    append(title, new DocumentEdit.Suggestion("stable-cue", "Bonsoir ?"));
    var batch = service.claim(title.id(), 1).orElseThrow();
    assertEquals(1, batch.changes().size());
    assertEquals("delete", batch.changes().getFirst().kind());
    assertNull(queue.read(title).getLast().batch());
  }

  @Test
  void discardingPendingWorkLeavesCompletedHistoryAndRejectsStaleQueues() throws Exception {
    service.complete(title.id(), claimAndWrite(), null);
    var history = queue.history(title);
    append(title, delete);
    assertThrows(IllegalArgumentException.class, () -> service.discard(title.id(), List.of()));
    service.discard(title.id(), queue.read(title).stream().map(c -> c.sequence()).toList());
    assertTrue(queue.read(title).isEmpty());
    assertEquals(history, queue.history(title));
  }

  @Test
  void completionCannotCloseAnotherTitlesBatch() throws Exception {
    var other = register("other");
    append(other, delete);
    long foreign = service.claim(other.id(), 50).orElseThrow().batch();
    assertThrows(
        NoSuchElementException.class,
        () -> service.complete(title.id(), foreign, new ReviewCompletion("not_adopted", null)));
    assertEquals(1, queue.read(title).size());
    assertEquals(1, queue.read(other).size());
    assertThrows(NoSuchElementException.class, () -> service.complete("unknown", foreign, null));
  }

  @Test
  void failedCompletionRollsBackEarlierDecisionsInTheSameBatch() throws Exception {
    append(title, new DocumentEdit.Suggestion("stable-cue", "Bonsoir ?"));
    var before = accepted.findHash(title.id()).orElseThrow();
    long batch = claimAndWrite();
    long last = queue.read(title).getLast().sequence();
    jdbc.execute(
        "CREATE TRIGGER fail_completion BEFORE UPDATE OF reviewed_at ON review_queue "
            + "WHEN NEW.sequence="
            + last
            + " BEGIN SELECT RAISE(ABORT, 'completion failure'); END");
    assertThrows(
        DataAccessException.class,
        () -> service.complete(title.id(), batch, new ReviewCompletion("applied", Map.of())));
    assertEquals(2, queue.read(title).size());
    assertTrue(queue.history(title).isEmpty());
    assertTrue(queue.openBatch(title).isPresent());
    assertEquals(before, accepted.findHash(title.id()).orElseThrow(), "accepted copy unchanged");
  }

  @Test
  void aTitleWithoutAnAcceptedCopyCannotBeServed() {
    jdbc.update("DELETE FROM accepted_documents WHERE title_id=?", title.id());
    var failure = assertThrows(DocumentConflictException.class, () -> service.document(title));
    assertTrue(failure.getMessage().contains("Refresh"));
  }

  @Test
  void completingWithAnUnchangedFileIsRefusedUnlessEveryEditIsNotAdopted() throws Exception {
    long batch = service.claim(title.id(), 50).orElseThrow().batch();
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.complete(title.id(), batch, new ReviewCompletion("applied", null)));
    assertTrue(failure.getMessage().contains("unchanged since this batch was claimed"));
    assertTrue(queue.openBatch(title).isPresent());
    service.complete(title.id(), batch, new ReviewCompletion("not_adopted", null));
    assertTrue(queue.openBatch(title).isEmpty());
  }

  @Test
  void importingATitleWithAnOpenBatchIsRefusedAndNamesTheBatch() throws Exception {
    long batch = service.claim(title.id(), 50).orElseThrow().batch();
    write(title, document("example").withTitle("Changed"));
    var failure =
        assertThrows(DocumentConflictException.class, () -> service.importFile(title.id()));
    assertTrue(failure.getMessage().contains("batch " + batch));
    service.complete(title.id(), batch, new ReviewCompletion("applied", null));
    assertEquals(DocumentImportService.Result.UNCHANGED, service.importFile(title.id()));
  }

  @Test
  void pendingTitlesListWorkAndOpenBatches() throws Exception {
    assertEquals(
        List.of(new ReviewQueue.PendingTitle(title.id(), "A generic title", 1, null)),
        service.pendingTitles());
    long batch = service.claim(title.id(), 50).orElseThrow().batch();
    assertEquals(batch, service.pendingTitles().getFirst().openBatch());
  }

  @Test
  void rowsQueuedBeforeReplayAreReviewOnly() throws Exception {
    jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json) VALUES(?,?,?,?)",
        title.id(),
        "2026-10-01T00:00:00Z",
        "edit",
        "[\"stable-cue\"]");
    servedDocuments.rebuild(title);
    var legacy = service.pending(title.id()).getLast();
    assertEquals("review", legacy.state());
    assertNull(legacy.command());
    assertEquals(0, service.document(title).video().cues().size());
  }
}
