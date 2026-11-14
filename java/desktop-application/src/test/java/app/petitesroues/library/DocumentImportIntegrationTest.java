package app.petitesroues.library;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.AcceptedDocuments.Result;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.support.StudyTestServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/** Accepted copies kept by Add, Refresh, import and removal, against real files and SQLite. */
class DocumentImportIntegrationTest {
  @TempDir Path root;
  StudyTestServices services;
  StudyDocumentCodec codec;
  JdbcTemplate jdbc;
  CatalogueStore catalogue;
  AcceptedDocumentStore accepted;
  ContentRepositoryStore repositories;
  DocumentImportService imports;
  ContentRepositoryService service;

  @BeforeEach
  void setup() {
    services = StudyTestServices.create(root);
    codec = services.codec;
    jdbc = services.jdbc;
    catalogue = services.titles;
    accepted = services.accepted;
    repositories = services.repositories;
    imports = services.imports;
    service = services.contentRepositories;
  }

  @AfterEach
  void close() {
    services.close();
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
    // The file is read; before the import transaction starts, another import accepts a copy.
    var racing =
        services.importsOver(
            new DocumentStore(services.reader) {
              @Override
              public Bytes readBytes(CatalogueEntry entry) throws IOException {
                var read = super.readBytes(entry);
                // A concurrent import saves its copy and rebuilds the served copy together.
                services.transactions.executeWithoutResult(
                    status -> {
                      accepted.save("one", concurrent, DocumentStore.sha256(concurrent), "now");
                      services.servedOver(this).rebuild(catalogue.find("one").orElseThrow());
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
  void refreshGivesATitleWithoutAnAcceptedCopyItsCopy() throws Exception {
    Path file = write("a", document("one"));
    String repository = add();
    jdbc.update("DELETE FROM accepted_documents");
    assertTrue(accepted.find("one").isEmpty());

    service.refresh(repository);

    assertAcceptedEqualsFile("one", file);
  }
}
