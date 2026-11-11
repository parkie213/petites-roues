package app.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** A registered folder on a real file system, reached through {@code java.nio}. */
public final class PathContentTree implements ContentTree<Path> {
  private final Path root;

  public PathContentTree(Path root) {
    this.root = root;
  }

  @Override
  public Path root() {
    return root;
  }

  @Override
  public List<Entry<Path>> children(Path folder) throws IOException {
    List<Entry<Path>> entries = new ArrayList<>();
    try (var listing = Files.list(folder)) {
      for (Path child : (Iterable<Path>) listing::iterator) {
        var attributes =
            Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() && !attributes.isRegularFile()) continue;
        entries.add(
            new Entry<>(
                child.getFileName().toString(),
                child,
                attributes.isDirectory(),
                attributes.isDirectory() ? 0 : attributes.size(),
                attributes.lastModifiedTime().toMillis()));
      }
    }
    return entries;
  }

  @Override
  public SeekableByteChannel open(Path file) throws IOException {
    return Files.newByteChannel(file, StandardOpenOption.READ);
  }
}
