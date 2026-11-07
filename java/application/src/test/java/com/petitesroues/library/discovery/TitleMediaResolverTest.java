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
  void aFilmInItsOwnFolderUsesThatFolderPosterAndAnyFileCanHaveANamedPoster() throws Exception {
    Path film = Files.write(folder.resolve("film.mkv"), new byte[] {1});
    assertNull(TitleMediaResolver.poster(film));
    Files.write(folder.resolve("posterior.jpg"), new byte[] {1});
    assertNull(TitleMediaResolver.poster(film));
    Path poster = Files.write(folder.resolve("Poster.WEBP"), new byte[] {1});
    assertEquals(poster, TitleMediaResolver.poster(film));
    Path named = Files.write(folder.resolve("film.jpg"), new byte[] {1});
    assertEquals(named, TitleMediaResolver.poster(film), "a poster named after the file wins");
    Files.write(folder.resolve("film.png"), new byte[] {1});
    assertThrows(IllegalArgumentException.class, () -> TitleMediaResolver.poster(film));
  }

  @Test
  void episodesSharingAFolderShareItsPosterAsTheSeriesPoster() throws Exception {
    Path show = Files.createDirectories(folder.resolve("show"));
    Path first = Files.write(show.resolve("s01e01.mp4"), new byte[] {1});
    Files.write(show.resolve("s01e02.mp4"), new byte[] {1});
    Path poster = Files.write(show.resolve("poster.jpg"), new byte[] {1});
    assertNull(TitleMediaResolver.poster(first), "a shared folder's poster is not an episode's");
    assertEquals(poster, TitleMediaResolver.seriesPoster(first, folder));
  }

  @Test
  void seriesPosterIsTheNearestAboveATitleFolderUpToTheRegisteredFolder() throws Exception {
    Path episode = Files.createDirectories(folder.resolve("show/s01e01/youtube-id"));
    Path video = Files.write(episode.resolve("video.mp4"), new byte[] {1});
    Files.write(episode.resolve("poster.jpg"), new byte[] {1});
    assertNull(TitleMediaResolver.seriesPoster(video, folder));
    Path root = Files.write(folder.resolve("poster.jpg"), new byte[] {1});
    assertEquals(root, TitleMediaResolver.seriesPoster(video, folder));
    Path show = Files.write(folder.resolve("show/poster.png"), new byte[] {1});
    assertEquals(show, TitleMediaResolver.seriesPoster(video, folder));
    Path nearer = Files.write(folder.resolve("show/s01e01/poster.jpg"), new byte[] {1});
    assertEquals(nearer, TitleMediaResolver.seriesPoster(video, folder));
  }
}
