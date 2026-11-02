package com.petitesroues.library.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryUsageScannerTest {
  @TempDir Path root;

  @Test
  void countsLibraryFilesButNotHiddenFoldersArchivesOrLinks() throws Exception {
    write("show/s01/video.mp4", 1000);
    write("show/s01/document.json", 200);
    write("poster.jpg", 30);
    write(".git/objects/pack", 5000);
    write("show/.stversions/old.mp4", 700);
    write("content-archive/runs/raw.json", 400);
    Files.createSymbolicLink(root.resolve("linked.mp4"), root.resolve("show/s01/video.mp4"));

    assertEquals(1230, new LibraryUsageScanner().bytes(root));
  }

  private void write(String path, int size) throws Exception {
    Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, new byte[size]);
  }
}
