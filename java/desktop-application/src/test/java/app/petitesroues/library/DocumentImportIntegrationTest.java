package app.petitesroues.library;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.DocumentImportService.Result;
import app.petitesroues.library.discovery.ContentRepositoryScanner;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.media.Ffmpeg;
import app.petitesroues.media.MediaProbe;
import app.petitesroues.media.ProcessRunner;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ServedDocuments;
import app.petitesroues.support.DatabaseFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Accepted copies kept by Add, Refresh, import and backfill, against real files and SQLite. */
class DocumentImportIntegrationTest {
  private final ExecutorService processExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  @TempDir Path root;
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;
  CatalogueStore catalogue;
  AcceptedDocumentStore accepted;
  ContentRepositoryStore repositories;
  DocumentImportService imports;
  ContentRepositoryService service;

  @BeforeEach
  void setup() {
    source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute("PRAGMA foreign_keys=ON");
    DatabaseFixtures.migrate(source);
    catalogue = new CatalogueStore(jdbc);
    accepted = new AcceptedDocumentStore(jdbc);
    repositories = new ContentRepositoryStore(jdbc);
    imports = imports(new DocumentStore(new StudyDocumentReader(codec)));
    service = service(imports);
  }

  @AfterEach
  void close() {
    source.destroy();
    processExecutor.close();
  }

  private TransactionTemplate transactions() {
    return new TransactionTemplate(new DataSourceTransactionManager(source));
  }

  private DocumentImportService imports(DocumentStore documents) {
    return new DocumentImportService(
        catalogue,
        documents,
        accepted,
        queue(),
        servedDocuments(documents),
        new RepositoryContentService(repositories, catalogue),
        transactions());
  }

  private ReviewQueue queue() {
    return new ReviewQueue(jdbc, StudyDocumentJson.newMapper());
  }

  private ServedDocuments servedDocuments(DocumentStore documents) {
    return new ServedDocuments(accepted, queue(), documents, codec);
  }

  private ContentRepositoryService service(DocumentImportService imports) {
    return new ContentRepositoryService(
        repositories,
        new ContentRepositoryScanner(),
        new StudyDocumentReader(codec),
        transactions(),
        new RepositoryContentService(repositories, catalogue),
        new ReviewQueue(jdbc, StudyDocumentJson.newMapper()),
        imports,
        new MediaStore(jdbc),
        new MediaProbe(
            new Ffmpeg(
                StudyDocumentJson.newMapper(),
                new ProcessRunner(processExecutor, Duration.ofSeconds(30)))));
  }

  private Path write(String folder, StudyDocument document) throws IOException {
    Path file = root.resolve(folder).resolve("document.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, codec.encode(document));
    return file;
  }

  private String add() throws IOException {
    return service.add(new ContentRepositoryService.Add(root.toString(), "Test")).id();
  }

  private void assertAcceptedEqualsFile(String titleId, Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    var copy = accepted.find(titleId).orElseThrow();
    assertArrayEquals(bytes, copy.json());
    assertEquals(DocumentStore.sha256(bytes), copy.sha256());
  }

  private void openBatch(String titleId) {
    jdbc.update(
        "INSERT INTO review_batches(title_id,base_sha256,created_at) VALUES(?,?,?)",
        titleId,
        "base",
        "now");
  }

  @Test
  void addAndRefreshKeepTheAcceptedCopyEqualToTheFile() throws Exception {
    Path file = write("a", document("one"));
    String repository = add();
    assertAcceptedEqualsFile("one", file);

    String importedAt = accepted.find("one").orElseThrow().importedAt();
    service.refresh(repository);
    assertEquals(importedAt, accepted.find("one").orElseThrow().importedAt(), "unchanged: no-op");

    write("a", document("one").withTitle("Changed"));
    service.refresh(repository);
    assertAcceptedEqualsFile("one", file);
    assertEquals("Changed", catalogue.find("one").orElseThrow().title());
  }

  @Test
  void aTitleWithAnOpenBatchKeepsItsAcceptedCopyThroughRefreshAndImport() throws Exception {
    Path file = write("a", document("one"));
    String repository = add();
    byte[] before = accepted.find("one").orElseThrow().json();
    openBatch("one");

    write("a", document("one").withTitle("Edited by an agent"));
    service.refresh(repository);
    assertArrayEquals(before, accepted.find("one").orElseThrow().json());
    assertEquals(Result.SKIPPED_OPEN_BATCH, imports.importFile("one"));
    assertArrayEquals(before, accepted.find("one").orElseThrow().json());

    jdbc.update("UPDATE review_batches SET completed_at='done'");
    assertEquals(Result.IMPORTED, imports.importFile("one"));
    assertAcceptedEqualsFile("one", file);
  }

  @Test
  void importReportsUnchangedWhenTheFileHasTheAcceptedHash() throws Exception {
    write("a", document("one"));
    add();
    assertEquals(Result.UNCHANGED, imports.importFile("one"));
  }

  @Test
  void aStaleImportChangesNothing() throws Exception {
    Path file = write("a", document("one"));
    add();
    write("a", document("one").withTitle("Newer"));
    byte[] concurrent = codec.encode(document("one").withTitle("Concurrent")).getBytes();
    var documents = new StudyDocumentReader(codec);
    // The file is read; before the import transaction starts, another import accepts a copy.
    var racing =
        imports(
            new DocumentStore(documents) {
              @Override
              public Bytes readBytes(CatalogueEntry entry) throws IOException {
                var read = super.readBytes(entry);
                // A concurrent import saves its copy and rebuilds the served copy together.
                transactions()
                    .executeWithoutResult(
                        status -> {
                          accepted.save("one", concurrent, DocumentStore.sha256(concurrent), "now");
                          servedDocuments(this).rebuild(catalogue.find("one").orElseThrow());
                        });
                return read;
              }
            });

    assertEquals(Result.CONFLICT, racing.importFile("one"));

    var copy = accepted.find("one").orElseThrow();
    assertArrayEquals(concurrent, copy.json());
    assertEquals("now", copy.importedAt());
    assertNotEquals("Newer", catalogue.find("one").orElseThrow().title());
    assertEquals(Result.IMPORTED, imports.importFile("one"), "a later import succeeds");
    assertAcceptedEqualsFile("one", file);
  }

  @Test
  void importValidatesTheFileAndLeavesTheCopyWhenItIsInvalid() throws Exception {
    Path file = write("a", document("one"));
    add();
    byte[] before = accepted.find("one").orElseThrow().json();
    Files.writeString(file, "{ not json");
    assertThrows(RuntimeException.class, () -> imports.importFile("one"));
    Files.writeString(file, codec.encode(document("other")));
    assertThrows(IOException.class, () -> imports.importFile("one"));
    assertArrayEquals(before, accepted.find("one").orElseThrow().json());
  }

  @Test
  void removingTheRepositoryRemovesTheAcceptedCopy() throws Exception {
    write("a", document("one"));
    String repository = add();
    assertEquals(1, accepted.hashesByDocumentId(repository).size());
    service.remove(repository);
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM accepted_documents", Integer.class));
  }

  @Test
  void backfillFillsMissingCopiesToleratesBadFilesAndIsRepeatable() throws Exception {
    Path one = write("a", document("one"));
    Path gone = write("b", document("gone"));
    write("c", document("bad"));
    add();
    jdbc.update("DELETE FROM accepted_documents");
    Files.delete(gone);
    Files.writeString(root.resolve("c/document.json"), "{ not json");

    assertEquals(1, imports.backfill());
    assertAcceptedEqualsFile("one", one);
    assertTrue(accepted.find("gone").isEmpty());
    assertTrue(accepted.find("bad").isEmpty());

    assertEquals(0, imports.backfill(), "titles already accepted are left alone");
    Files.writeString(root.resolve("c/document.json"), codec.encode(document("bad")));
    assertEquals(1, imports.backfill(), "a repaired file is picked up on the next start");
    assertEquals(0, imports.backfill());
  }
}
