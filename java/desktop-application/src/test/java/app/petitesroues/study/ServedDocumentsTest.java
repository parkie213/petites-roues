package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.TestDocuments.catalogueEntry;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.DocumentImportService;
import app.petitesroues.library.RepositoryContentService;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.CueChange;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the served copy promises beyond the invariant that {@link ServedInvariantTest} checks: reads
 * never replay, a lost row is rebuilt on the next read, removal takes the copy with the title, and
 * a database upgraded from schema version 3 serves what the old replay would have served.
 */
class ServedDocumentsTest {
  @TempDir Path root;
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;
  StudyDocumentCodec codec;
  DocumentStore store;
  AcceptedDocumentStore accepted;
  ReviewQueue queue;
  ServedDocuments servedDocuments;
  ReviewQueueService review;
  DocumentImportService imports;
  CatalogueStore catalogue;
  TransactionTemplate transactions;
  CatalogueEntry entry;

  /** Wires the services over a database with the schema migrated up to {@code version}. */
  void open(String version) {
    codec = new StudyDocumentCodec();
    source = new SingleConnectionDataSource("jdbc:sqlite:" + root.resolve("db.sqlite"), true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute("PRAGMA foreign_keys=ON");
    Flyway.configure().dataSource(source).target(version).load().migrate();
    catalogue = new CatalogueStore(jdbc);
    store = new DocumentStore(new StudyDocumentReader(codec));
    accepted = new AcceptedDocumentStore(jdbc);
    queue = new ReviewQueue(jdbc, StudyDocumentJson.newMapper());
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
    review =
        new ReviewQueueService(
            catalogue, store, accepted, imports, queue, servedDocuments, transactions);
  }

  @BeforeEach
  void setup() throws Exception {
    open("latest");
    entry = catalogueEntry(jdbc, "title", root);
    catalogue.saveTitle(entry);
    Files.createDirectories(entry.documentFile().getParent());
    Files.write(
        entry.documentFile(), codec.encode(document("title")).getBytes(StandardCharsets.UTF_8));
    imports.importFile(entry.id());
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  private byte[] storedServed() {
    return jdbc.queryForObject(
        "SELECT json FROM served_documents WHERE title_id=?", byte[].class, entry.id());
  }

  private void queueEdit(DocumentEdit edit) {
    transactions.executeWithoutResult(
        status -> {
          queue.append(entry, edit);
          servedDocuments.rebuild(entry);
        });
  }

  @Test
  void aReadReturnsTheStoredServedCopyAndNeverReplaysTheQueue() {
    queueEdit(new DocumentEdit.DeleteVideoCue("stable-cue"));
    assertTrue(review.document(entry).video().cues().isEmpty());

    // Replace the accepted bytes with another valid document under the same hash: a replay would
    // now serve it, a lookup does not.
    byte[] other =
        codec.encode(document("title").withTitle("Not served")).getBytes(StandardCharsets.UTF_8);
    jdbc.update("UPDATE accepted_documents SET json=? WHERE title_id=?", other, entry.id());

    var read = review.effective(entry);
    assertEquals("A generic title", read.document().title());
    assertTrue(read.document().video().cues().isEmpty());
    assertEquals(List.of("pending"), read.pending().stream().map(CueChange::state).toList());
    assertArrayEquals(storedServed(), review.servedBytes(entry));
  }

  @Test
  void aMissingOrStaleServedRowIsRebuiltByTheNextRead() {
    queueEdit(new DocumentEdit.DeleteVideoCue("stable-cue"));
    byte[] expected = storedServed();

    jdbc.update("DELETE FROM served_documents");
    assertTrue(review.document(entry).video().cues().isEmpty());
    assertArrayEquals(expected, storedServed());

    jdbc.update(
        "UPDATE served_documents SET accepted_sha256='stale', json=?", (Object) new byte[0]);
    assertArrayEquals(expected, review.servedBytes(entry));
    assertEquals(accepted.findHash(entry.id()).orElseThrow(), stamp());
  }

  private String stamp() {
    return jdbc.queryForObject(
        "SELECT accepted_sha256 FROM served_documents WHERE title_id=?", String.class, entry.id());
  }

  @Test
  void theStartupRebuildCoversMissingStaleAndPendingTitlesAndIsRepeatable() {
    assertEquals(List.of(), accepted.titlesNeedingServed());
    jdbc.update("DELETE FROM served_documents");
    assertEquals(List.of("title"), accepted.titlesNeedingServed());
    assertEquals(1, review.rebuildServedCopies());
    assertEquals(List.of(), accepted.titlesNeedingServed());

    queueEdit(new DocumentEdit.DeleteVideoCue("stable-cue"));
    byte[] expected = storedServed();
    assertEquals(List.of("title"), accepted.titlesNeedingServed(), "pending rows are rebuilt");
    assertEquals(1, review.rebuildServedCopies());
    assertEquals(1, review.rebuildServedCopies());
    assertArrayEquals(expected, storedServed());
  }

  @Test
  void removingTheTitleRemovesItsServedCopy() {
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM served_documents", Integer.class));
    jdbc.update("DELETE FROM titles WHERE id=?", entry.id());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM served_documents", Integer.class));
  }

  @Test
  void aDatabaseUpgradedFromVersionThreeServesWhatTheOldReplayServed() throws Exception {
    source.destroy();
    root.resolve("db.sqlite").toFile().delete();
    open("3");
    entry = catalogueEntry(jdbc, "title", root);
    catalogue.saveTitle(entry);
    byte[] original = codec.encode(document("title")).getBytes(StandardCharsets.UTF_8);
    accepted.save("title", original, DocumentStore.sha256(original), "now");
    // Version 3 stored only commands; the last one no longer applies after the deletion.
    var commands =
        List.of(
            new DocumentEdit.Suggestion("stable-cue", "Heard otherwise"),
            new DocumentEdit.DeleteVideoCue("stable-cue"),
            new DocumentEdit.Comment("stable-cue", "Too late"));
    commands.forEach(command -> queue.append(entry, command));
    var replayed = store.decode(original);
    replayed = DocumentEdits.apply(replayed, commands.get(0));
    replayed = DocumentEdits.apply(replayed, commands.get(1));
    byte[] expected = codec.encode(replayed).getBytes(StandardCharsets.UTF_8);

    source.destroy();
    open("latest");
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM served_documents", Integer.class));
    assertEquals(
        List.of(0, 0, 0), jdbc.queryForList("SELECT conflict FROM review_queue", Integer.class));
    assertEquals(List.of("title"), accepted.titlesNeedingServed());

    assertEquals(1, review.rebuildServedCopies());

    assertArrayEquals(expected, storedServed());
    assertArrayEquals(expected, review.servedBytes(entry));
    assertEquals(
        List.of("pending", "pending", "conflict"),
        review.pending("title").stream().map(CueChange::state).toList());
  }
}
