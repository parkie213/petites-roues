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
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Discovers the files a registered folder offers the library, without reading content or accessing
 * the database: candidate study documents (any {@code .json}) and media (supported videos and
 * {@code .cbz} comics). A folder may hold any mix. Traversal skips hidden directories, {@code
 * node_modules}, {@code content-archive} (archived preparation runs are not live titles) and
 * symbolic links, and never modifies files.
 */
@Component
public class ContentRepositoryScanner {
  private static final Set<String> SKIPPED_DIRECTORIES = Set.of("content-archive", "node_modules");

  /**
   * What one walk of a folder found, each list sorted by path for deterministic indexing.
   *
   * @param documents every regular {@code .json} file; the caller decides which are study documents
   * @param videos every regular file with a supported video extension
   * @param comics every regular {@code .cbz} file
   */
  public record Discovery(List<Path> documents, List<Path> videos, List<Path> comics) {
    public Discovery {
      documents = List.copyOf(documents);
      videos = List.copyOf(videos);
      comics = List.copyOf(comics);
    }
  }

  /**
   * Walks the folder once. The caller owns reading, validation and registration of what it finds.
   *
   * @throws IllegalArgumentException if the root is not a directory
   * @throws IOException if directory traversal fails
   */
  public Discovery scan(Path root) throws IOException {
    if (!Files.isDirectory(root)) throw new IllegalArgumentException("Folder is missing: " + root);
    List<Path> documents = new ArrayList<>(),
        videos = new ArrayList<>(),
        comics = new ArrayList<>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) return FileVisitResult.CONTINUE;
            String name = dir.getFileName().toString();
            return name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name)
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile() || file.getFileName().toString().startsWith("."))
              return FileVisitResult.CONTINUE;
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json")) documents.add(file);
            else if (name.endsWith(".cbz")) comics.add(file);
            else if (TitleMediaResolver.isVideo(file)) videos.add(file);
            return FileVisitResult.CONTINUE;
          }
        });
    for (var list : List.of(documents, videos, comics))
      list.sort(Comparator.comparing(Path::toString));
    return new Discovery(documents, videos, comics);
  }
}
