package com.petitesroues.library;

import static com.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.petitesroues.comic.ReadingProgressStore;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.library.persistence.MediaStore;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueLocation;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.library.IndexedMedia;
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
  @Mock MediaStore media;
  @TempDir Path root;

  private LibraryService library() {
    return new LibraryService(
        repository, documents, repositories, null, playback, reading, new TitleMediaService(media));
  }

  private CatalogueLocation located(String fingerprint, String type) {
    return new CatalogueLocation("repo", "Repo", root.toString(), false, 60.0, type, fingerprint);
  }

  private IndexedMedia indexed(
      String repositoryId, Path folder, String path, String kind, String fingerprint) {
    return new IndexedMedia(
        repositoryId, folder.toString(), path, kind, 1, 1, fingerprint, 60.0, "aac", true);
  }

  @Test
  void unknownEntriesRejectDocumentReadsButHaveNoMedia() throws Exception {
    when(repository.find("unknown")).thenReturn(Optional.empty());
    var library = library();
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
            "document.json",
            located("v1:film", "video"));
    var video = indexed("repo", root, "video.mp4", "video", "v1:film");
    when(repository.all()).thenReturn(List.of(entry));
    when(media.all()).thenReturn(List.of(video));
    when(media.withFingerprint("v1:film")).thenReturn(List.of(video));
    when(repository.find("film")).thenReturn(Optional.of(entry));
    var study = document("film");
    when(documents.document(entry)).thenReturn(study);
    var library = library();

    var catalogue = library.catalogue();
    assertEquals(1, catalogue.size());
    assertEquals("Example film", catalogue.getFirst().title());
    assertTrue(catalogue.getFirst().videoAvailable());
    assertEquals(Map.of(), catalogue.getFirst().unavailable());
    verifyNoInteractions(documents);
    assertEquals(study, library.document("film"));
    assertEquals(root.resolve("video.mp4"), library.media("film").orElseThrow());
  }

  @Test
  void mediaLinksByFingerprintAloneNeverByFolder(@TempDir Path elsewhere) throws Exception {
    Files.write(root.resolve("beside.mp4"), new byte[] {0});
    Files.write(elsewhere.resolve("copy.mp4"), new byte[] {0});
    Files.write(root.resolve("own.mp4"), new byte[] {0});
    var unreferenced =
        new CatalogueEntry(
            "plain", "Plain", null, null, null, null, "a.json", located(null, "video"));
    var film =
        new CatalogueEntry(
            "film", "Film", null, null, null, null, "b.json", located("v1:film", "video"));
    var book =
        new CatalogueEntry(
            "book", "Book", null, null, null, null, "c.json", located("v1:film", "comic"));
    when(repository.find("plain")).thenReturn(Optional.of(unreferenced));
    when(repository.find("film")).thenReturn(Optional.of(film));
    when(repository.find("book")).thenReturn(Optional.of(book));
    var other = indexed("other", elsewhere, "copy.mp4", "video", "v1:film");
    var own = indexed("repo", root, "own.mp4", "video", "v1:film");
    var gone = indexed("repo", root, "gone.mp4", "video", "v1:film");
    when(media.withFingerprint("v1:film")).thenReturn(List.of(gone, other, own));
    var library = library();

    assertTrue(library.media("plain").isEmpty(), "no reference, no media, even beside it");
    assertEquals(root.resolve("own.mp4"), library.media("film").orElseThrow());
    assertTrue(library.media("book").isEmpty(), "a comic never links to a video");
    when(media.withFingerprint("v1:film")).thenReturn(List.of(gone, other));
    assertEquals(elsewhere.resolve("copy.mp4"), library.media("film").orElseThrow());
    verifyNoInteractions(documents);
  }

  @Test
  void everyMediaFileIsListedWithItsWarningsAndUnplayableAudioWarnsOnTheTitle() throws Exception {
    var folder = new ContentRepository("repo", "Folder", root.toString(), null, 1, 0, 3);
    Files.write(root.resolve("film.json"), new byte[] {0});
    Files.write(root.resolve("film.mkv"), new byte[] {0});
    var film =
        new CatalogueEntry(
            "film", "Film", null, null, null, null, "film.json", located("v1:film", "video"));
    var dubbed =
        new IndexedMedia(
            "repo", root.toString(), "film.mkv", "video", 1, 1, "v1:film", 60.0, "ac3", false);
    var stray =
        new IndexedMedia(
            "repo", root.toString(), "stray.mkv", "video", 1, 1, "v1:stray", 30.0, "dts", false);
    var comic =
        new IndexedMedia(
            "repo", root.toString(), "book.cbz", "comic", 1, 1, "v1:book", null, null, null);
    when(repositories.list()).thenReturn(List.of(folder));
    when(repository.all()).thenReturn(List.of(film));
    when(media.all()).thenReturn(List.of(comic, dubbed, stray));

    var contents = library().library().repositories().getFirst();

    var title = contents.entries().getFirst();
    assertTrue(title.videoAvailable());
    assertEquals(Map.of("audio", "No audio track this player can play (AC3)"), title.unavailable());
    assertEquals(
        List.of("book.cbz", "film.mkv", "stray.mkv"),
        contents.media().stream().map(item -> item.relativePath()).toList());
    assertEquals(List.of("No study document"), contents.media().getFirst().warnings());
    assertEquals("film", contents.media().get(1).titleId());
    assertEquals(
        List.of("No audio track this player can play (AC3)"), contents.media().get(1).warnings());
    assertEquals(
        List.of("No study document", "No audio track this player can play (DTS)"),
        contents.media().get(2).warnings());
  }

  @Test
  void postersComeFromRefreshAndAnAmbiguousFolderIsAWarning() throws Exception {
    var folder = new ContentRepository("repo", "Folder", root.toString(), null, 1, 0, 2);
    Files.write(root.resolve("film.json"), new byte[] {0});
    Files.write(root.resolve("film.mkv"), new byte[] {0});
    Files.write(root.resolve("film.jpg"), new byte[] {0});
    var film =
        new CatalogueEntry(
            "film", "Film", null, null, null, null, "film.json", located("v1:film", "video"));
    String problem = "Several posters named poster in show — keep one image";
    var linked =
        new IndexedMedia(
            "repo",
            root.toString(),
            "film.mkv",
            "video",
            1,
            1,
            "v1:film",
            60.0,
            "aac",
            true,
            "film.jpg",
            null,
            null);
    var episode =
        new IndexedMedia(
            "repo",
            root.toString(),
            "show/e1.mkv",
            "video",
            1,
            1,
            "v1:e1",
            60.0,
            "aac",
            true,
            null,
            null,
            problem);
    when(repositories.list()).thenReturn(List.of(folder));
    when(repository.all()).thenReturn(List.of(film));
    when(media.all()).thenReturn(List.of(linked, episode));
    when(repository.find("film")).thenReturn(Optional.of(film));
    when(media.withFingerprint("v1:film")).thenReturn(List.of(linked));

    var library = library();
    var contents = library.library().repositories().getFirst();

    assertTrue(contents.entries().getFirst().posterAvailable());
    assertEquals(root.resolve("film.jpg"), library.poster("film").orElseThrow());
    assertEquals(List.of("No study document", problem), contents.media().get(1).warnings());
  }

  @Test
  void unmatchedTitlesSayWhetherTheyLackAReferenceOrTheMedia() throws Exception {
    Files.write(root.resolve("a.json"), new byte[] {0});
    when(repository.all())
        .thenReturn(
            List.of(
                new CatalogueEntry(
                    "a", "A", null, null, null, null, "a.json", located(null, "video")),
                new CatalogueEntry(
                    "b", "B", null, null, null, null, "a.json", located("v1:x", "comic"))));
    var problems = library().catalogue().stream().map(item -> item.unavailable().get("title"));
    assertEquals(
        List.of(
            "No reference media recorded — run prepare/reference.py, then Refresh",
            "CBZ not found — add the folder that holds it, then Refresh"),
        problems.toList());
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
    var library = library();
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
    var library = library();

    var progress = new HashMap<String, Double>();
    for (var item : library.catalogue()) progress.put(item.id(), item.progress());

    assertEquals(0.25, progress.get("film"));
    assertEquals(1.0, progress.get("long"), "positions past the reference duration are full");
    assertNull(progress.get("new"));
    assertNull(progress.get("clip"), "no bar without a known duration");
    assertEquals(0.5, progress.get("book"));
    assertNull(progress.get("old-book"), "comic positions saved before fractions show no bar");
  }

  @Test
  void eachFolderListsItsOwnDocumentsAndAllItsMediaNamingTheirTitles(@TempDir Path videosRoot)
      throws Exception {
    var documents = new ContentRepository("repo", "documents", root.toString(), null, 2, 0);
    var videos = new ContentRepository("media", "media", videosRoot.toString(), null, 0, 0, 1);
    Files.write(root.resolve("film.json"), new byte[] {0});
    Files.write(root.resolve("notes-only.json"), new byte[] {0});
    Files.createDirectories(videosRoot.resolve("films"));
    Files.write(videosRoot.resolve("films/Film.mkv"), new byte[] {0});
    var film =
        new CatalogueEntry(
            "film", "Film", null, null, null, null, "film.json", located("v1:film", "video"));
    var unlinked =
        new CatalogueEntry(
            "notes", "Notes", null, null, null, null, "notes-only.json", located(null, "video"));
    when(repositories.list()).thenReturn(List.of(documents, videos));
    when(repository.all()).thenReturn(List.of(film, unlinked));
    when(media.all())
        .thenReturn(List.of(indexed("media", videosRoot, "films/Film.mkv", "video", "v1:film")));

    var folders = library().library().repositories();

    assertEquals(
        List.of("film", "notes"),
        folders.getFirst().entries().stream().map(e -> e.id()).toList(),
        "documents stay in the folder that holds them");
    var linked = folders.getFirst().entries().getFirst();
    assertEquals("media", linked.mediaRepositoryId());
    assertEquals("films/Film.mkv", linked.mediaPath());
    assertTrue(folders.get(1).entries().isEmpty());
    var item = folders.get(1).media().getFirst();
    assertEquals("film", item.titleId(), "linked media names its title");
    assertTrue(item.warnings().isEmpty());
  }
}
