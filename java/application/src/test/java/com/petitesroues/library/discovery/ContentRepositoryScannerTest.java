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
  void discoversPathsWithoutDecodingAndSkipsGitArchiveAndSymbolicLinks() throws Exception {
    Path second = root.resolve("b/document.json");
    Path first = root.resolve("a/document.json");
    for (Path file :
        List.of(
            second,
            first,
            root.resolve(".git/document.json"),
            root.resolve("content-archive/runs/old/document.json"))) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, "not JSON: discovery must not decode this");
    }
    Files.writeString(root.resolve("other.json"), "ignored");
    Files.createDirectory(root.resolve("linked"));
    Files.createSymbolicLink(root.resolve("linked/document.json"), first);
    Files.createSymbolicLink(root.resolve("linked-directory"), first.getParent());

    assertEquals(List.of(first, second), new ContentRepositoryScanner().scan(root));
  }
}
