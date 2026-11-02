package com.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;

/**
 * Measures how much disk a content repository's library takes, without reading content or accessing
 * the database. Counts regular files beneath the root, skipping hidden directories (such as .git
 * and sync folders) and content-archive, as title discovery does; symbolic links are not followed.
 */
@Component
public class LibraryUsageScanner {
  /**
   * Total size in bytes of the library files beneath {@code root}; files that vanish or cannot be
   * read during the walk are skipped rather than failing the total.
   *
   * @throws IOException if the root itself cannot be walked
   */
  public long bytes(Path root) throws IOException {
    long[] total = {0};
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) return FileVisitResult.CONTINUE;
            String name = dir.getFileName().toString();
            return name.startsWith(".") || name.equals("content-archive")
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) total[0] += attrs.size();
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException failure) {
            return FileVisitResult.CONTINUE;
          }
        });
    return total[0];
  }
}
