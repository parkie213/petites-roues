package app.petitesroues.pages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Finds the Markdown pages in one folder at a content repository's root: the regular {@code *.md}
 * files directly inside it. The folder name ({@code quizzes}, {@code notes}) says what the pages
 * are, so other Markdown (a README, research notes) is never listed. Reads no content and never
 * follows symbolic links.
 */
@Component
public class MarkdownPageScanner {
  /** Page file paths in [root]'s [folder], sorted by name; empty when the folder is absent. */
  public List<Path> scan(Path root, String folder) throws IOException {
    Path pages = root.resolve(folder);
    if (!Files.isDirectory(pages, LinkOption.NOFOLLOW_LINKS)) return List.of();
    try (Stream<Path> files = Files.list(pages)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".md"))
          .filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
          .sorted()
          .toList();
    }
  }
}
