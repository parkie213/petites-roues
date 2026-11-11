package app.petitesroues.library.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentScannerTest {
  @TempDir Path root;

  private ContentScanner.Discovery<Path> scan() throws Exception {
    return ContentScanner.scan(new PathContentTree(root));
  }

  private ContentScanner.Media<Path> media(String relativePath) throws Exception {
    return scan().media().stream()
        .filter(item -> item.relativePath().equals(relativePath))
        .findFirst()
        .orElseThrow();
  }

  private static String path(ContentScanner.Found<Path> found) {
    return found == null ? null : found.relativePath();
  }

  private Path write(String relativePath) throws Exception {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    return Files.write(file, new byte[] {1});
  }

  @Test
  void findsDocumentsAndMediaInOneWalkSkippingHiddenArchiveAndSymbolicLinks() throws Exception {
    for (String file :
        List.of(
            "b/lunch.json",
            "a/document.json",
            ".git/document.json",
            "node_modules/pkg/package.json",
            "content-archive/runs/old/document.json",
            "media/episode.mp4.part",
            "media/.hidden.mp4")) write(file);
    write("media/show/Episode 1.MKV");
    write("media/volume.cbz");
    Files.createDirectory(root.resolve("linked"));
    Files.createSymbolicLink(root.resolve("linked/document.json"), root.resolve("a/document.json"));
    Files.createSymbolicLink(root.resolve("linked-directory"), root.resolve("a"));

    var found = scan();
    assertEquals(
        List.of("a/document.json", "b/lunch.json"),
        found.documents().stream().map(ContentScanner.Found::relativePath).toList());
    assertEquals(
        List.of("media/show/Episode 1.MKV", "media/volume.cbz"),
        found.media().stream().map(ContentScanner.Media::relativePath).toList());
    assertEquals(
        List.of(MediaKind.VIDEO, MediaKind.COMIC),
        found.media().stream().map(ContentScanner.Media::kind).toList());
    assertEquals(5, found.bytes(), "every file seen counts, media or not; skipped ones do not");
  }

  @Test
  void mediaIsRecognisedByExtensionWhateverItsCase() {
    assertEquals(MediaKind.VIDEO, MediaKind.of("Youtube title.MKV"));
    assertEquals(MediaKind.VIDEO, MediaKind.of("episode.webm"));
    assertNull(MediaKind.of("video.mp4.part"));
    assertEquals(MediaKind.COMIC, MediaKind.of("Volume.CBZ"));
  }

  @Test
  void aFilmInItsOwnFolderUsesThatFolderPosterAndAnyFileCanHaveANamedPoster() throws Exception {
    write("film/film.mkv");
    assertNull(media("film/film.mkv").poster());
    write("film/posterior.jpg");
    assertNull(media("film/film.mkv").poster());
    write("film/Poster.WEBP");
    assertEquals("film/Poster.WEBP", path(media("film/film.mkv").poster()));
    write("film/film.jpg");
    assertEquals(
        "film/film.jpg",
        path(media("film/film.mkv").poster()),
        "a poster named after the file wins");
    write("film/film.png");
    var ambiguous = media("film/film.mkv");
    assertNull(ambiguous.poster());
    assertTrue(ambiguous.posterProblem().contains("Several posters"));
  }

  @Test
  void episodesSharingAFolderShareItsPosterAsTheSeriesPoster() throws Exception {
    write("show/s01e01.mp4");
    write("show/s01e02.mp4");
    write("show/poster.jpg");
    var first = media("show/s01e01.mp4");
    assertNull(first.poster(), "a shared folder's poster is not an episode's");
    assertEquals("show/poster.jpg", path(first.seriesPoster()));
  }

  @Test
  void seriesPosterIsTheNearestAboveATitleFolderUpToTheRegisteredFolder() throws Exception {
    String video = "show/s01e01/youtube-id/video.mp4";
    write(video);
    write("show/s01e01/youtube-id/poster.jpg");
    assertNull(media(video).seriesPoster());
    write("poster.jpg");
    assertEquals("poster.jpg", path(media(video).seriesPoster()));
    write("show/poster.png");
    assertEquals("show/poster.png", path(media(video).seriesPoster()));
    write("show/s01e01/poster.jpg");
    assertEquals("show/s01e01/poster.jpg", path(media(video).seriesPoster()));
    write("show/s01e01/poster.webp");
    assertNull(media(video).seriesPoster());
    assertNotNull(media(video).posterProblem(), "an ambiguous folder is reported, not skipped");
  }
}
