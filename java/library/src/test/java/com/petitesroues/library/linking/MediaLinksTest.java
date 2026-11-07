package com.petitesroues.library.linking;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.library.discovery.MediaKind;
import com.petitesroues.library.linking.MediaLinks.Candidate;
import java.util.List;
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
}
