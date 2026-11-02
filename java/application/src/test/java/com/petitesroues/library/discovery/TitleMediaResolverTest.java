package com.petitesroues.library.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TitleMediaResolverTest {
  @TempDir Path folder;

  @Test
  void downloadedVideoAppearsAndCanBeRenamedOrRemovedWithoutAnIndexUpdate() throws Exception {
    Path document = folder.resolve("document.json");
    assertNull(TitleMediaResolver.video(document));
    Path download = Files.write(folder.resolve("Youtube title.MKV"), new byte[] {1});
    assertEquals(download, TitleMediaResolver.video(document));
    Path renamed = Files.move(download, folder.resolve("French edition.mp4"));
    assertEquals(renamed, TitleMediaResolver.video(document));
    Files.delete(renamed);
    assertNull(TitleMediaResolver.video(document));
  }

  @Test
  void ambiguityIsExplicitAndResolvesAsSoonAsTheExtraVideoIsRemoved() throws Exception {
    Path document = folder.resolve("document.json");
    Path first = Files.write(folder.resolve("one.mp4"), new byte[] {1});
    Path extra = Files.write(folder.resolve("two.webm"), new byte[] {1});
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> TitleMediaResolver.video(document))
            .getMessage()
            .contains("Multiple videos"));
    Files.delete(extra);
    assertEquals(first, TitleMediaResolver.video(document));
  }

  @Test
  void mediaInAnotherTitleFolderAndPartialDownloadsAreIgnored() throws Exception {
    Path nested = Files.createDirectory(folder.resolve("another-title"));
    Files.write(nested.resolve("video.mp4"), new byte[] {1});
    Files.write(folder.resolve("video.mp4.part"), new byte[] {1});
    Files.createDirectory(folder.resolve("not-a-video.mp4"));
    assertNull(TitleMediaResolver.video(folder.resolve("document.json")));
  }

  @Test
  void posterIsOptionalAndMustBeUnambiguous() throws Exception {
    Path document = folder.resolve("document.json");
    assertNull(TitleMediaResolver.poster(document));
    Files.write(folder.resolve("posterior.jpg"), new byte[] {1});
    assertNull(TitleMediaResolver.poster(document));
    Path poster = Files.write(folder.resolve("Poster.WEBP"), new byte[] {1});
    assertEquals(poster, TitleMediaResolver.poster(document));
    Files.write(folder.resolve("poster.png"), new byte[] {1});
    assertThrows(IllegalArgumentException.class, () -> TitleMediaResolver.poster(document));
  }

  @Test
  void seriesPosterIsTheNearestAboveTheTitleFolderAndNeverTheRepositoryRoot() throws Exception {
    Path episode = Files.createDirectories(folder.resolve("show/s01e01/youtube-id"));
    Path document = episode.resolve("document.json");
    Files.write(folder.resolve("poster.jpg"), new byte[] {1});
    Files.write(episode.resolve("poster.jpg"), new byte[] {1});
    assertNull(TitleMediaResolver.seriesPoster(document, folder));
    Path show = Files.write(folder.resolve("show/poster.png"), new byte[] {1});
    assertEquals(show, TitleMediaResolver.seriesPoster(document, folder));
    Path nearer = Files.write(folder.resolve("show/s01e01/poster.jpg"), new byte[] {1});
    assertEquals(nearer, TitleMediaResolver.seriesPoster(document, folder));
  }
}
