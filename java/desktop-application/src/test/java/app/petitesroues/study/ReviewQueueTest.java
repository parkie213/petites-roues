package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.TestDocuments.catalogueEntry;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.LibraryUpdateMonitor;
import app.petitesroues.library.persistence.CatalogueStore;
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
    title = register("example");
    queue = new ReviewQueue(jdbc, mapper);
    service =
        new ReviewQueueService(
            new LibraryUpdateMonitor(),
            new CatalogueStore(jdbc),
            new DocumentStore(new StudyDocumentReader(codec)),
            queue,
            new TransactionTemplate(new DataSourceTransactionManager(source)));
    queue.append(title, delete);
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  private CatalogueEntry register(String id) throws Exception {
    var entry = catalogueEntry(jdbc, id, root);
    new CatalogueStore(jdbc).saveTitle(entry);
    write(entry, document(id));
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
    queue.append(title, new DocumentEdit.Suggestion("stable-cue", "Bonsoir ?"));
    var batch = service.claim(title.id(), 1).orElseThrow();
    assertEquals(1, batch.changes().size());
    assertEquals("delete", batch.changes().getFirst().kind());
    assertNull(queue.read(title).getLast().batch());
  }

  @Test
  void discardingPendingWorkLeavesCompletedHistoryAndRejectsStaleQueues() throws Exception {
    service.complete(title.id(), claimAndWrite(), null);
    var history = queue.history(title);
    queue.append(title, delete);
    assertThrows(IllegalArgumentException.class, () -> service.discard(title.id(), List.of()));
    service.discard(title.id(), queue.read(title).stream().map(c -> c.sequence()).toList());
    assertTrue(queue.read(title).isEmpty());
    assertEquals(history, queue.history(title));
  }

  @Test
  void completionCannotCloseAnotherTitlesBatch() throws Exception {
    var other = register("other");
    queue.append(other, delete);
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
    queue.append(title, new DocumentEdit.Suggestion("stable-cue", "Bonsoir ?"));
    var before = queue.read(title);
    long batch = claimAndWrite();
    long last = before.getLast().sequence();
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
  }

  @Test
  void pendingTitlesListWorkAndOpenBatches() throws Exception {
    assertEquals(
        List.of(new ReviewQueue.PendingTitle(title.id(), "Title", 1, null)),
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
    var legacy = service.pending(title.id()).getLast();
    assertEquals("review", legacy.state());
    assertNull(legacy.command());
    assertEquals(0, service.document(title).video().cues().size());
  }
}
