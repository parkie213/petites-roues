package com.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Discovers study-document paths without reading content or accessing the database. Traversal skips
 * .git and content-archive directories (archived preparation runs are not live titles) and symbolic
 * links, and never modifies files.
 */
@Component
public class ContentRepositoryScanner {
  private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", "content-archive");

  /**
   * Returns regular {@code document.json} paths beneath the root, sorted by path for deterministic
   * indexing. The caller owns reading, validation and registration of the discovered documents.
   *
   * @throws IllegalArgumentException if the root is not a directory
   * @throws IOException if directory traversal fails
   */
  public List<Path> scan(Path root) throws IOException {
    if (!Files.isDirectory(root))
      throw new IllegalArgumentException("Repository directory is missing: " + root);
    List<Path> discoveredDocuments = new ArrayList<>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return !dir.equals(root) && SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!attrs.isRegularFile() || !file.getFileName().toString().equals("document.json"))
              return FileVisitResult.CONTINUE;
            discoveredDocuments.add(file);
            return FileVisitResult.CONTINUE;
          }
        });
    discoveredDocuments.sort(Comparator.comparing(Path::toString));
    return discoveredDocuments;
  }
}
