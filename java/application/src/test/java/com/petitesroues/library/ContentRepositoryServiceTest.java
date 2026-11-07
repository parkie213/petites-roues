package com.petitesroues.library;

import static com.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.library.discovery.ContentRepositoryScanner;
import com.petitesroues.library.discovery.ContentScanner;
import com.petitesroues.library.discovery.PathContentTree;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.library.persistence.MediaStore;
import com.petitesroues.media.MediaProbe;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoDocument;
import com.petitesroues.model.document.VideoReference;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.library.IndexedMedia;
import com.petitesroues.study.ReviewQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Tests refresh decisions and error handling with collaborators isolated from files and SQL. */
@ExtendWith(MockitoExtension.class)
class ContentRepositoryServiceTest {
  static final String ID = "repository";
  final Path root = Path.of("repository-content").toAbsolutePath();
  final Path first = root.resolve("a/document.json");
  final Path second = root.resolve("b/document.json");
  final ContentRepository repository =
      new ContentRepository(ID, "Repository", root.toString(), "old", 1, 0);
  @Mock ContentRepositoryStore repositories;
  @Mock CatalogueStore catalogue;
  @Mock ContentRepositoryScanner scanner;
  @Mock StudyDocumentReader reader;
  @Mock RepositoryContentService registration;
  @Mock ReviewQueue queue;
  @Mock MediaStore media;
  @Mock MediaProbe probe;
  @Mock TransactionTemplate transactions;
  @Mock TransactionStatus transactionStatus;
  ContentRepositoryService service;

  @BeforeEach
  void setup() throws IOException {
    service =
        new ContentRepositoryService(
            repositories, scanner, reader, transactions, registration, queue, media, probe);
    lenient().when(reader.isStudyDocument(any(Path.class))).thenReturn(true);
  }

  private static ContentScanner.Discovery<Path> found(Path... documents) {
    return new ContentScanner.Discovery<>(
        Stream.of(documents)
            .map(
                document -> new ContentScanner.Found<>(document.getFileName().toString(), document))
            .toList(),
        List.of(),
        0);
  }

  private void knownRepository() {
    when(repositories.find(ID)).thenReturn(repository);
  }

  private void oneDocument() throws IOException {
    knownRepository();
    when(scanner.scan(root)).thenReturn(found(first));
    when(reader.read(first)).thenReturn(document("title"));
  }

  private void executeTransaction() {
    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> work = invocation.getArgument(0);
              work.accept(transactionStatus);
              return null;
            })
        .when(transactions)
        .executeWithoutResult(any());
  }

  @Test
  void duplicateIdsAreRejectedBeforeStartingATransaction() throws Exception {
    knownRepository();
    when(scanner.scan(root)).thenReturn(found(first, second));
    when(reader.read(first)).thenReturn(document("title"));
    when(reader.read(second)).thenReturn(document("title"));
    var failure = assertThrows(IllegalArgumentException.class, () -> service.refresh(ID));
    assertTrue(failure.getMessage().contains("Duplicate title ID"));
    verifyNoInteractions(transactions, registration, catalogue);
    verify(repositories, never()).markTitlesMissing(anyString());
  }

  @Test
  void invalidDocumentReportsItsRelativePathAndPreservesTheCause() throws Exception {
    knownRepository();
    when(scanner.scan(root)).thenReturn(found(first));
    var cause = new IllegalArgumentException("Unsupported document format");
    when(reader.read(first)).thenThrow(cause);
    var failure = assertThrows(IllegalArgumentException.class, () -> service.refresh(ID));
    assertTrue(failure.getMessage().contains(root.relativize(first).toString()));
    assertSame(cause, failure.getCause());
    verifyNoInteractions(transactions, registration, catalogue);
  }

  @Test
  void readFailurePropagatesWithoutStartingATransaction() throws Exception {
    knownRepository();
    when(scanner.scan(root)).thenReturn(found(first));
    var failure = new IOException("Unreadable document");
    when(reader.read(first)).thenThrow(failure);
    assertSame(failure, assertThrows(IOException.class, () -> service.refresh(ID)));
    verifyNoInteractions(transactions, registration, catalogue);
  }

  @Test
  void unknownRepositoryDoesNotStartDiscovery() {
    when(repositories.find("unknown")).thenThrow(new NoSuchElementException("Unknown repository"));
    assertThrows(NoSuchElementException.class, () -> service.refresh("unknown"));
    verifyNoInteractions(scanner, reader, transactions, registration, catalogue);
  }

  @Test
  void missingDirectoryFailurePropagatesWithoutReadingDocuments() throws Exception {
    knownRepository();
    var failure = new IllegalArgumentException("Repository directory is missing");
    when(scanner.scan(root)).thenThrow(failure);
    assertSame(failure, assertThrows(IllegalArgumentException.class, () -> service.refresh(ID)));
    verifyNoInteractions(reader, transactions, registration, catalogue);
  }

  @Test
  void traversalIoFailurePropagatesWithoutStartingATransaction() throws Exception {
    knownRepository();
    var failure = new IOException("Cannot traverse repository");
    when(scanner.scan(root)).thenThrow(failure);
    assertSame(failure, assertThrows(IOException.class, () -> service.refresh(ID)));
    verifyNoInteractions(reader, transactions, registration, catalogue);
  }

  @Test
  void ownedAndNewTitlesCanBeRegisteredWithTheirReferenceMetadata() throws Exception {
    knownRepository();
    executeTransaction();
    when(reader.read(first)).thenReturn(document("title"));
    when(scanner.scan(root)).thenReturn(found(first, second));
    var base = document("new-title");
    var video = base.video();
    var added =
        new StudyDocument(
            base.id(),
            base.title(),
            base.sourceLanguage(),
            base.translationLanguage(),
            base.notesLanguage(),
            null,
            null,
            new VideoDocument(null, null, new VideoReference(null, null, 120.0), video.cues()));
    when(reader.read(second)).thenReturn(added);
    when(registration.register(
            new RepositoryContentService.Registration(first.toString(), true), ID))
        .thenReturn(
            new CatalogueEntry("local-title", "Title", null, null, null, null, first.toString()));
    when(registration.register(
            new RepositoryContentService.Registration(second.toString(), true), ID))
        .thenReturn(
            new CatalogueEntry(
                "local-new-title", "New title", null, null, null, null, second.toString()));

    assertSame(repository, service.refresh(ID));

    verify(registration)
        .register(new RepositoryContentService.Registration(first.toString(), true), ID);
    verify(registration)
        .register(new RepositoryContentService.Registration(second.toString(), true), ID);
    verify(repositories).recordRefresh(eq(ID), anyString());
  }

  @Test
  void jsonThatIsNotAStudyDocumentIsIgnored() throws Exception {
    knownRepository();
    executeTransaction();
    when(scanner.scan(root)).thenReturn(found(first));
    when(reader.isStudyDocument(first)).thenReturn(false);
    service.refresh(ID);
    verify(reader, never()).read(any());
    verifyNoInteractions(registration);
    verify(media).replace(ID, List.of());
  }

  @Test
  void refreshProbesOnlyNewOrChangedMediaAndReplacesTheFolderIndex(@TempDir Path folder)
      throws Exception {
    var here = new ContentRepository(ID, "Repository", folder.toString(), "old", 0, 0);
    when(repositories.find(ID)).thenReturn(here);
    executeTransaction();
    Path kept = Files.write(folder.resolve("kept.mp4"), new byte[] {1});
    Path changed = Files.write(folder.resolve("changed.mkv"), new byte[] {1, 2});
    long keptTime = Files.getLastModifiedTime(kept).toMillis();
    var previous =
        List.of(
            new IndexedMedia(
                ID,
                folder.toString(),
                "kept.mp4",
                "video",
                1,
                keptTime,
                "v1:kept",
                60.0,
                "aac",
                true),
            new IndexedMedia(
                ID, folder.toString(), "changed.mkv", "video", 1, 0, "v1:old", 60.0, "aac", true),
            new IndexedMedia(
                ID, folder.toString(), "gone.mp4", "video", 1, 0, "v1:gone", 60.0, "aac", true));
    when(media.inRepository(ID)).thenReturn(previous);
    when(scanner.scan(folder)).thenReturn(ContentScanner.scan(new PathContentTree(folder)));
    when(probe.video(changed)).thenReturn(new MediaProbe.Probe("v1:new", 30.0, "ac3", false));

    service.refresh(ID);

    verify(probe, never()).video(kept);
    verify(media)
        .replace(
            ID,
            List.of(
                new IndexedMedia(
                    null,
                    folder.toString(),
                    "changed.mkv",
                    "video",
                    2,
                    Files.getLastModifiedTime(changed).toMillis(),
                    "v1:new",
                    30.0,
                    "ac3",
                    false),
                new IndexedMedia(
                    null,
                    folder.toString(),
                    "kept.mp4",
                    "video",
                    1,
                    keptTime,
                    "v1:kept",
                    60.0,
                    "aac",
                    true)));
  }
}
