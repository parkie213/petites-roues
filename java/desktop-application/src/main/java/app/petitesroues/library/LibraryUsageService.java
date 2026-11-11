package app.petitesroues.library;

import app.petitesroues.library.discovery.LibraryUsageScanner;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.library.LibraryUsage;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reports the library's disk usage and the free space left, measured on request from the registered
 * repositories' folders. Repositories whose folder is missing are left out; nothing is cached or
 * written.
 */
@Service
public class LibraryUsageService {
  private final ContentRepositoryStore repositories;
  private final LibraryUsageScanner scanner;

  public LibraryUsageService(ContentRepositoryStore repositories, LibraryUsageScanner scanner) {
    this.repositories = repositories;
    this.scanner = scanner;
  }

  /** The library's size and each disk's free space, grouping repositories that share a disk. */
  public LibraryUsage usage() throws IOException {
    long bytes = 0;
    Map<FileStore, List<String>> disks = new LinkedHashMap<>();
    for (var repository : repositories.list()) {
      Path root = Path.of(repository.localPath());
      if (!Files.isDirectory(root)) continue;
      bytes += scanner.bytes(root);
      disks
          .computeIfAbsent(Files.getFileStore(root), store -> new ArrayList<>())
          .add(repository.name());
    }
    List<LibraryUsage.Disk> free = new ArrayList<>();
    for (var disk : disks.entrySet())
      free.add(
          new LibraryUsage.Disk(
              disk.getValue(), disk.getKey().getUsableSpace(), disk.getKey().getTotalSpace()));
    return new LibraryUsage(bytes, free);
  }
}
