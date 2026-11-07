package com.petitesroues.library;

import static com.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.petitesroues.comic.ReadingProgressStore;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueLocation;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.playback.PlaybackProgressStore;
import com.petitesroues.study.ReviewQueueService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {
  @Mock CatalogueStore repository;
  @Mock ReviewQueueService documents;
  @Mock ContentRepositoryStore repositories;
  @Mock PlaybackProgressStore playback;
  @Mock ReadingProgressStore reading;
  @TempDir Path root;

  @Test
  void unknownEntriesRejectDocumentReadsButHaveNoMedia() throws Exception {
    when(repository.find("unknown")).thenReturn(Optional.empty());
    var library = new LibraryService(repository, documents, repositories, null, playback, reading);
    assertThrows(NoSuchElementException.class, () -> library.document("unknown"));
    assertTrue(library.media("unknown").isEmpty());
    verifyNoInteractions(documents);
  }

  @Test
  void listsAndLoadsAnAvailableTitle() throws Exception {
    for (String file : List.of("document.json", "video.mp4")) {
      Files.write(root.resolve(file), new byte[] {0, 0, 1, 0});
    }
    var entry =
        new CatalogueEntry(
            "film",
            "Example film",
            null,
            null,
            null,
            null,
            root.resolve("document.json").toString());
    when(repository.all()).thenReturn(List.of(entry));
    when(repository.find("film")).thenReturn(Optional.of(entry));
    var study = document("film");
    when(documents.document(entry)).thenReturn(study);
    var library = new LibraryService(repository, documents, repositories, null, playback, reading);

    var catalogue = library.catalogue();
    assertEquals(1, catalogue.size());
    assertEquals("Example film", catalogue.getFirst().title());
    verifyNoInteractions(documents);
    assertEquals(study, library.document("film"));
    assertEquals(root.resolve("video.mp4"), library.media("film").orElseThrow());
  }

  @Test
  void missingMediaIsEmptyButMalformedMediaIsRejected() throws Exception {
    var entry =
        new CatalogueEntry(
            "film", "Film", null, null, null, null, root.resolve("document.json").toString());
    when(repository.find("film")).thenReturn(Optional.of(entry));
    var library = new LibraryService(repository, documents, repositories, null, playback, reading);
    assertTrue(library.media("film").isEmpty());
    Files.write(root.resolve("one.mp4"), new byte[] {0});
    Files.write(root.resolve("two.mkv"), new byte[] {0});
    assertThrows(IllegalArgumentException.class, () -> library.media("film"));
    verifyNoInteractions(documents);
  }

  @Test
  void libraryGroupsIndexedEntriesAndRetainsEmptyRepositories() throws Exception {
    var populated = new ContentRepository("repo", "Collection", root.toString(), null, 1, 1);
    var empty = new ContentRepository("empty", "Empty", root.toString(), null, 0, 0);
    var location = new CatalogueLocation("repo", "Collection", root.toString(), true, null);
    var owned =
        new CatalogueEntry("owned", "Owned", null, null, null, null, "document.json", location);
    var standalone =
        new CatalogueEntry(
            "standalone",
            "Standalone",
            null,
            null,
            null,
            null,
            root.resolve("standalone.json").toString());
    when(repositories.list()).thenReturn(List.of(populated, empty));
    when(repository.all()).thenReturn(List.of(owned, standalone));
    var library = new LibraryService(repository, documents, repositories, null, playback, reading);
    var result = library.library().repositories();
    assertEquals(List.of(populated, empty), result.stream().map(r -> r.repository()).toList());
    assertEquals(List.of("owned"), result.getFirst().entries().stream().map(e -> e.id()).toList());
    assertFalse(result.getFirst().entries().getFirst().documentAvailable());
    assertTrue(result.get(1).entries().isEmpty());
    assertEquals(2, library.catalogue().size());
    verifyNoInteractions(documents);
  }

  @Test
  void progressIsThePositionOverTheDurationOrTheStoredComicFraction() throws Exception {
    var video = new CatalogueLocation("repo", "Repo", root.toString(), false, 100.0);
    var unknownLength = new CatalogueLocation("repo", "Repo", root.toString(), false, null);
    var comic = new CatalogueLocation("repo", "Repo", root.toString(), false, null, "comic");
    when(repository.all())
        .thenReturn(
            List.of(
                new CatalogueEntry(
                    "film", "Film", null, null, null, null, "film/document.json", video),
                new CatalogueEntry(
                    "long", "Long", null, null, null, null, "long/document.json", video),
                new CatalogueEntry(
                    "new", "New", null, null, null, null, "new/document.json", video),
                new CatalogueEntry(
                    "clip", "Clip", null, null, null, null, "clip/document.json", unknownLength),
                new CatalogueEntry(
                    "book", "Book", null, null, null, null, "book/document.json", comic),
                new CatalogueEntry(
                    "old-book", "Old", null, null, null, null, "old/document.json", comic)));
    when(playback.positions()).thenReturn(Map.of("film", 25.0, "long", 250.0, "clip", 5.0));
    when(reading.fractions()).thenReturn(Map.of("book", 0.5));
    var library = new LibraryService(repository, documents, repositories, null, playback, reading);

    var progress = new HashMap<String, Double>();
    for (var item : library.catalogue()) progress.put(item.id(), item.progress());

    assertEquals(0.25, progress.get("film"));
    assertEquals(1.0, progress.get("long"), "positions past the reference duration are full");
    assertNull(progress.get("new"));
    assertNull(progress.get("clip"), "no bar without a known duration");
    assertEquals(0.5, progress.get("book"));
    assertNull(progress.get("old-book"), "comic positions saved before fractions show no bar");
  }
}
