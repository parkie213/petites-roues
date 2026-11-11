package app.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Discovers a registered folder on this computer with the library's shared rules ({@link
 * ContentScanner}): candidate study documents, media and their posters. Reads no file contents and
 * never accesses the database or modifies files. A component so refresh workflows can be tested
 * with a fixed discovery.
 */
@Component
public class ContentRepositoryScanner {
  /**
   * Walks the folder once. The caller owns reading, validation and registration of what it finds.
   *
   * @throws IllegalArgumentException if the root is not a directory
   * @throws IOException if directory traversal fails
   */
  public ContentScanner.Discovery<Path> scan(Path root) throws IOException {
    if (!Files.isDirectory(root)) throw new IllegalArgumentException("Folder is missing: " + root);
    return ContentScanner.scan(new PathContentTree(root));
  }
}
