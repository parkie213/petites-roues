package com.petitesroues.library.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TitleMediaResolverTest {
  @TempDir Path folder;

  @Test
  void videosAreRecognisedByExtensionWhateverTheirCase() {
    assertTrue(TitleMediaResolver.isVideo(folder.resolve("Youtube title.MKV")));
    assertTrue(TitleMediaResolver.isVideo(folder.resolve("episode.webm")));
    assertFalse(TitleMediaResolver.isVideo(folder.resolve("video.mp4.part")));
    assertFalse(TitleMediaResolver.isVideo(folder.resolve("volume.cbz")));
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
