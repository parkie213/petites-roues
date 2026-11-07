package com.petitesroues.library.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentRepositoryScannerTest {
  @TempDir Path root;

  @Test
  void findsDocumentsAndMediaInOneWalkSkippingHiddenArchiveAndSymbolicLinks() throws Exception {
    Path second = root.resolve("b/lunch.json");
    Path first = root.resolve("a/document.json");
    for (Path file :
        List.of(
            second,
            first,
            root.resolve(".git/document.json"),
            root.resolve("node_modules/pkg/package.json"),
            root.resolve("content-archive/runs/old/document.json"))) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, "not JSON: discovery must not decode this");
    }
    Files.createDirectories(root.resolve("media/show"));
    Path video = Files.write(root.resolve("media/show/Episode 1.MKV"), new byte[] {1});
    Path comic = Files.write(root.resolve("media/volume.cbz"), new byte[] {1});
    Files.write(root.resolve("media/episode.mp4.part"), new byte[] {1});
    Files.write(root.resolve("media/.hidden.mp4"), new byte[] {1});
    Files.createDirectory(root.resolve("linked"));
    Files.createSymbolicLink(root.resolve("linked/document.json"), first);
    Files.createSymbolicLink(root.resolve("linked-directory"), first.getParent());

    var found = new ContentRepositoryScanner().scan(root);
    assertEquals(List.of(first, second), found.documents());
    assertEquals(List.of(video), found.videos());
    assertEquals(List.of(comic), found.comics());
  }
}
