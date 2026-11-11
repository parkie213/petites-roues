package app.petitesroues.library.linking;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.discovery.MediaKind;
import app.petitesroues.library.linking.MediaLinks.Candidate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaLinksTest {
  private static Candidate<String> file(String path, MediaKind kind, String fingerprint) {
    return new Candidate<>(path, kind, fingerprint, path.substring(0, path.indexOf('/')));
  }

  @Test
  void aDocumentLinksOnlyToMediaOfItsKindWithItsFingerprint() {
    var media =
        List.of(
            file("media/a.cbz", MediaKind.COMIC, "v1:a"),
            file("media/b.mp4", MediaKind.VIDEO, "v1:b"),
            file("media/a.mp4", MediaKind.VIDEO, "v1:a"));
    assertEquals(
        Optional.of("media/a.mp4"), MediaLinks.choose(MediaKind.VIDEO, "v1:a", "docs", media));
    assertEquals(
        Optional.of("media/a.cbz"), MediaLinks.choose(MediaKind.COMIC, "v1:a", "docs", media));
    assertEquals(Optional.empty(), MediaLinks.choose(MediaKind.VIDEO, "v1:c", "docs", media));
    assertEquals(
        Optional.empty(),
        MediaLinks.choose(MediaKind.VIDEO, null, "docs", media),
        "a document without a reference never links");
  }

  @Test
  void aCopyInTheDocumentsOwnFolderWinsThenTheFirstInOrder() {
    var copies =
        List.of(
            file("drive/film.mkv", MediaKind.VIDEO, "v1:f"),
            file("nas/film.mkv", MediaKind.VIDEO, "v1:f"),
            file("study/film.mkv", MediaKind.VIDEO, "v1:f"));
    assertEquals(
        Optional.of("study/film.mkv"), MediaLinks.choose(MediaKind.VIDEO, "v1:f", "study", copies));
    assertEquals(
        Optional.of("drive/film.mkv"), MediaLinks.choose(MediaKind.VIDEO, "v1:f", "docs", copies));
  }

  @Test
  void groupingByKeyKeepsOrderAndSeparatesKinds() {
    var groups =
        MediaLinks.byKey(
            List.of(
                file("a/x.mp4", MediaKind.VIDEO, "v1:x"),
                file("a/x.cbz", MediaKind.COMIC, "v1:x"),
                file("b/x.mp4", MediaKind.VIDEO, "v1:x")));
    assertEquals(
        List.of("a/x.mp4", "b/x.mp4"),
        groups.get(MediaLinks.key(MediaKind.VIDEO, "v1:x")).stream()
            .map(Candidate::media)
            .toList());
    assertEquals(1, groups.get(MediaLinks.key(MediaKind.COMIC, "v1:x")).size());
    assertNull(MediaLinks.key(MediaKind.VIDEO, null));
  }

  private static Candidate<String> linked(
      String path, MediaKind kind, String fingerprint, String to) {
    return new Candidate<>(path, kind, fingerprint, path.substring(0, path.indexOf('/')), to);
  }

  @Test
  void aManualLinkWinsOverTheFingerprintOnBothSides() {
    var media =
        List.of(
            file("media/s01e01.mp4", MediaKind.VIDEO, "v1:one"),
            linked("media/s01e02.mp4", MediaKind.VIDEO, "v1:two", "episode-2"),
            linked("media/rip.mp4", MediaKind.VIDEO, "v1:one", "episode-3"));
    assertEquals(
        Optional.of("media/s01e02.mp4"),
        MediaLinks.choose("episode-2", MediaKind.VIDEO, "v1:zero", "docs", media),
        "the linked file, though its fingerprint differs");
    assertEquals(
        Optional.of("media/s01e02.mp4"),
        MediaLinks.choose("episode-2", MediaKind.VIDEO, null, "docs", media),
        "a document without a reference links by hand");
    assertEquals(
        Optional.of("media/s01e01.mp4"),
        MediaLinks.choose("episode-1", MediaKind.VIDEO, "v1:one", "docs", media),
        "a file linked to another document is not matched by fingerprint");
    assertEquals(
        Optional.empty(), MediaLinks.choose("episode-4", MediaKind.VIDEO, "v1:two", "docs", media));
    assertEquals(
        Optional.empty(),
        MediaLinks.choose("episode-2", MediaKind.COMIC, null, "docs", media),
        "a link to another kind does not count");
  }

  @Test
  void severalManualLinksPreferTheDocumentsFolderThenOrder() {
    var copies =
        List.of(
            linked("drive/a.mp4", MediaKind.VIDEO, "v1:a", "doc"),
            linked("study/a.mp4", MediaKind.VIDEO, "v1:b", "doc"));
    assertEquals(
        Optional.of("study/a.mp4"),
        MediaLinks.choose("doc", MediaKind.VIDEO, null, "study", copies));
    assertEquals(
        Optional.of("drive/a.mp4"),
        MediaLinks.choose("doc", MediaKind.VIDEO, null, "docs", copies));
  }

  @Test
  void aFilesOwnerIsItsManualLinkElseTheFingerprintsDocument() {
    var owners = Map.of(MediaLinks.key(MediaKind.VIDEO, "v1:one"), "episode-1");
    assertEquals(
        "episode-1", MediaLinks.owner(file("m/a.mp4", MediaKind.VIDEO, "v1:one"), owners::get));
    assertEquals(
        "episode-3",
        MediaLinks.owner(linked("m/a.mp4", MediaKind.VIDEO, "v1:one", "episode-3"), owners::get));
    assertNull(MediaLinks.owner(file("m/b.mp4", MediaKind.VIDEO, "v1:two"), owners::get));
  }
}
