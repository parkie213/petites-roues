package com.petitesroues.library;

import static com.petitesroues.support.DocumentFixtures.document;
import static com.petitesroues.support.TestDocuments.edit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.petitesroues.comic.ReadingProgressStore;
import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.library.discovery.ContentRepositoryScanner;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.library.persistence.MediaStore;
import com.petitesroues.media.Ffmpeg;
import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.media.MediaProbe;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoDocument;
import com.petitesroues.model.document.VideoReference;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.progress.PlaybackProgress;
import com.petitesroues.playback.PlaybackProgressStore;
import com.petitesroues.study.DocumentEditingService;
import com.petitesroues.study.DocumentStore;
import com.petitesroues.study.ReviewQueue;
import com.petitesroues.study.ReviewQueueService;
import com.petitesroues.support.DatabaseFixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Exercises repository reconciliation with real files, SQLite and transaction rollback. */
class ContentRepositoryRefreshIntegrationTest {
  @TempDir Path root;
  SingleConnectionDataSource source;
  JdbcTemplate jdbc;
  CatalogueStore catalogue;
  ContentRepositoryStore repositories;
  ContentRepositoryScanner scanner;
  StudyDocumentReader reader;
  ContentRepositoryService service;
  JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
    jdbc = new JdbcTemplate(source);
    jdbc.execute("PRAGMA foreign_keys=ON");
    DatabaseFixtures.migrate(source);
    catalogue = new CatalogueStore(jdbc);
    repositories = new ContentRepositoryStore(jdbc);
    scanner = new ContentRepositoryScanner();
    reader = new StudyDocumentReader();
    service = service(new RepositoryContentService(repositories, catalogue, reader));
  }

  @AfterEach
  void close() {
    source.destroy();
  }

  private ContentRepositoryService service(RepositoryContentService registration) {
    return new ContentRepositoryService(
        repositories,
        scanner,
        reader,
        new TransactionTemplate(new DataSourceTransactionManager(source)),
        registration,
        new ReviewQueue(jdbc, mapper),
        new MediaStore(jdbc),
        new MediaProbe(new Ffmpeg(mapper)));
  }

  private Path write(String folder, StudyDocument document) throws IOException {
    Path file = root.resolve(folder).resolve("document.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, new StudyDocumentCodec().encode(document));
    return file;
  }

  private ContentRepository add() throws IOException {
    return service.add(new ContentRepositoryService.Add(root.toString(), "Test repository"));
  }

  private List<?> snapshot() {
    return List.of(
        jdbc.queryForList("SELECT * FROM titles ORDER BY id"),
        jdbc.queryForList("SELECT * FROM repositories ORDER BY id"),
        jdbc.queryForList("SELECT * FROM review_queue ORDER BY sequence"),
        jdbc.queryForList("SELECT * FROM playback_progress ORDER BY title_id"));
  }

  private void addLocalState(String id) {
    jdbc.update(
        "INSERT INTO review_queue(title_id,timestamp,kind,cue_ids_json,suggestion) VALUES(?,?,?,?,?)",
        id,
        "review-time",
        "edit",
        "[]",
        null);
    jdbc.update(
        "INSERT INTO playback_progress(title_id,position_seconds,updated_at) VALUES(?,?,?)",
        id,
        42.5,
        "progress-time");
  }

  @Test
  void catalogueLookupReturnsEmptyForUnknownEntriesAndTheIndexedEntryWhenPresent()
      throws Exception {
    assertTrue(catalogue.find("unknown").isEmpty());
    write("a", document("existing"));
    add();
    assertEquals("existing", catalogue.find("existing").orElseThrow().documentId());
    assertTrue(catalogue.find("unknown").isEmpty());
  }

  @Test
  void copiedDocumentsKeepEditsReviewsAndProgressIndependent() throws Exception {
    Path firstFile = write("first/title", document("shared"));
    Path secondFile = write("second/title", document("shared"));
    var firstRepo =
        service.add(new ContentRepositoryService.Add(root.resolve("first").toString(), "First"));
    var secondRepo =
        service.add(new ContentRepositoryService.Add(root.resolve("second").toString(), "Second"));
    var first = catalogue.findDocument(firstRepo.id(), "shared").orElseThrow();
    var second = catalogue.findDocument(secondRepo.id(), "shared").orElseThrow();
    assertNotEquals(first.id(), second.id());
    assertEquals("shared", second.documentId());
    var queue = new ReviewQueue(jdbc, mapper);
    var store = new DocumentStore(reader);
    var served =
        new ReviewQueueService(
            catalogue,
            store,
            queue,
            new TransactionTemplate(new DataSourceTransactionManager(source)));
    var library =
        new LibraryService(
            catalogue,
            served,
            repositories,
            store,
            new PlaybackProgressStore(jdbc),
            new ReadingProgressStore(jdbc),
            new TitleMediaService(new MediaStore(jdbc)));
    var editing = new DocumentEditingService(catalogue, served, queue);
    byte[] originalFirst = Files.readAllBytes(firstFile);
    var cue =
        library
            .videoDocument(second.id())
            .video()
            .cues()
            .getFirst()
            .withLine(s -> s.withSource("Changed in second copy"));
    editing.editCue(second.id(), cue.line().id(), edit(cue));
    assertArrayEquals(originalFirst, Files.readAllBytes(firstFile));
    assertEquals(
        "Changed in second copy",
        library.videoDocument(second.id()).video().cues().getFirst().line().source());
    assertEquals("shared", library.document(second.id()).id());
    assertEquals(1, queue.read(second).size());
    assertTrue(queue.read(first).isEmpty());
    var progress = new PlaybackProgressStore(jdbc);
    progress.save(second.id(), new PlaybackProgress(42, "now"));
    assertEquals(0, progress.find(first.id()).positionSeconds());
    assertEquals(42, progress.find(second.id()).positionSeconds());
    service.refresh(secondRepo.id());
    assertEquals(second.id(), catalogue.findDocument(secondRepo.id(), "shared").orElseThrow().id());
    assertEquals(1, queue.read(second).size());
    assertArrayEquals(originalFirst, Files.readAllBytes(firstFile));
    assertEquals(
        "Changed in second copy",
        library.videoDocument(second.id()).video().cues().getFirst().line().source());
    assertEquals(1, queue.read(second).size());
    service.remove(firstRepo.id());
    assertEquals(42, progress.find(second.id()).positionSeconds());
    assertEquals("shared", library.document(second.id()).id());
  }

  @Test
  void overlappingRepositoryRootsCannotRegisterTheSamePhysicalDocumentTwice() throws Exception {
    write("nested/title", document("shared"));
    service.add(new ContentRepositoryService.Add(root.resolve("nested").toString(), "Nested"));
    var before = snapshot();
    assertThrows(IllegalArgumentException.class, this::add);
    assertEquals(before, snapshot());
  }

  @Test
  void refreshUpdatesMetadataAddsTitlesAndRecordsSuccessWithoutChangingSourceFiles()
      throws Exception {
    Path file = write("a", document("existing"));
    var repository = add();
    addLocalState("existing");
    jdbc.update("UPDATE repositories SET last_refreshed_at='old' WHERE id=?", repository.id());
    var original = document("existing");
    var changed =
        new StudyDocument(
            original.id(),
            "Updated",
            original.sourceLanguage(),
            original.translationLanguage(),
            original.notesLanguage(),
            null,
            "Cartoons",
            null,
            new VideoDocument(
                null, null, new VideoReference(null, null, 120.0), original.video().cues()));
    write("a", changed);
    write("b", document("new-title"));
    byte[] expected = Files.readAllBytes(file);
    var reviews = jdbc.queryForList("SELECT * FROM review_queue");
    var progress = jdbc.queryForList("SELECT * FROM playback_progress");

    var result = service.refresh(repository.id());

    assertEquals(2, result.titles());
    assertEquals(0, result.missing());
    assertNotEquals("old", result.lastRefreshedAt());
    Instant.parse(result.lastRefreshedAt());
    var entry = catalogue.find("existing").orElseThrow();
    assertEquals("Updated", entry.title());
    assertEquals("Cartoons", entry.shelf(), "the document's shelf is indexed");
    assertEquals(changed.sourceLanguage(), entry.sourceLanguage(), "and its source language");
    assertNull(catalogue.find("new-title").orElseThrow().shelf(), "no shelf means the default");
    assertEquals(120.0, entry.location().durationSeconds());
    assertTrue(catalogue.contains("new-title"));
    assertEquals(reviews, jdbc.queryForList("SELECT * FROM review_queue"));
    assertEquals(progress, jdbc.queryForList("SELECT * FROM playback_progress"));
    assertArrayEquals(expected, Files.readAllBytes(file));
  }

  @Test
  void missingTitlesRetainRowsReviewsAndProgressAndBecomePresentWhenRestored() throws Exception {
    Path file = write("a", document("existing"));
    var repository = add();
    addLocalState("existing");
    var reviews = jdbc.queryForList("SELECT * FROM review_queue");
    var progress = jdbc.queryForList("SELECT * FROM playback_progress");
    Files.delete(file);

    var missing = service.refresh(repository.id());
    assertEquals(1, missing.titles());
    assertEquals(1, missing.missing());
    assertTrue(catalogue.find("existing").orElseThrow().location().missing());
    assertEquals(reviews, jdbc.queryForList("SELECT * FROM review_queue"));
    assertEquals(progress, jdbc.queryForList("SELECT * FROM playback_progress"));

    write("a", document("existing"));
    assertEquals(0, service.refresh(repository.id()).missing());
    assertEquals(1, catalogue.all().size());
    assertFalse(catalogue.find("existing").orElseThrow().location().missing());
    assertEquals(reviews, jdbc.queryForList("SELECT * FROM review_queue"));
  }

  @Test
  void malformedDocumentRejectsRefreshBeforeUpdatingEarlierValidDocuments() throws Exception {
    write("a", document("existing"));
    var repository = add();
    var before = snapshot();
    var changed = document("existing").withTitle("Must not reach the index");
    write("a", changed);
    Path invalid = write("b", document("invalid"));
    Files.writeString(invalid, "{\"sourceLanguage\": \"fr\", invalid JSON");
    var failure =
        assertThrows(IllegalArgumentException.class, () -> service.refresh(repository.id()));
    assertTrue(failure.getMessage().contains("b/document.json"));
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertInstanceOf(JacksonException.class, failure.getCause().getCause());
    assertEquals(before, snapshot());
  }

  @Test
  void registrationFailureAfterWritesRollsBackMetadataMembershipsAndTimestamp() throws Exception {
    write("a", document("existing"));
    var repository = add();
    addLocalState("existing");
    var before = snapshot();
    var changed = document("existing").withTitle("Must roll back");
    write("a", changed);
    Path second = write("b", document("new-title"));
    var registration = spy(new RepositoryContentService(repositories, catalogue, reader));
    doThrow(new IOException("Second registration failed"))
        .when(registration)
        .register(
            new RepositoryContentService.Registration(second.toString(), true), repository.id());
    var failing = service(registration);

    assertThrows(UncheckedIOException.class, () -> failing.refresh(repository.id()));
    verify(registration)
        .register(
            new RepositoryContentService.Registration(
                root.resolve("a/document.json").toString(), true),
            repository.id());
    assertEquals(before, snapshot());
  }

  @Test
  void timestampWriteFailureRollsBackAlreadyRegisteredNewTitles() throws Exception {
    write("a", document("existing"));
    var repository = add();
    var before = snapshot();
    write("b", document("new-title"));
    jdbc.execute(
        "CREATE TRIGGER fail_refresh BEFORE UPDATE OF last_refreshed_at ON repositories BEGIN SELECT RAISE(ABORT, 'timestamp failure'); END");
    assertThrows(DataAccessException.class, () -> service.refresh(repository.id()));
    assertEquals(before, snapshot());
  }

  @Test
  void refreshIndexesMediaWithItsFingerprintDurationAndAudio() throws Exception {
    Path folder = Files.createDirectories(root.resolve("films"));
    for (String name : List.of("aac.mp4", "ac3.mp4"))
      try (var fixture = getClass().getResourceAsStream("/media/" + name)) {
        Files.copy(fixture, folder.resolve(name));
      }
    var repository = add();
    var media = new MediaStore(jdbc).inRepository(repository.id());
    assertEquals(
        List.of("films/aac.mp4", "films/ac3.mp4"),
        media.stream().map(m -> m.relativePath()).toList());
    var playable = media.getFirst();
    assertEquals(MediaFingerprint.of(folder.resolve("aac.mp4")), playable.fingerprint());
    assertEquals("aac", playable.audioCodecs());
    assertEquals(true, playable.audioPlayable());
    assertTrue(playable.durationSeconds() > 0);
    var dubbed = media.get(1);
    assertEquals(false, dubbed.audioPlayable());
    assertEquals("No audio track this player can play (AC3)", dubbed.audioWarning());
    assertEquals(2, repositories.find(repository.id()).media());

    Files.delete(folder.resolve("ac3.mp4"));
    service.refresh(repository.id());
    assertEquals(1, new MediaStore(jdbc).inRepository(repository.id()).size());
  }
}
