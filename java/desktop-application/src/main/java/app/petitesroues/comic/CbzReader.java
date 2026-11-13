package app.petitesroues.comic;

import app.petitesroues.library.comic.CbzArchiveReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Desktop file adapter for the shared CBZ reader. Each operation opens a filesystem channel and
 * closes it before return; shared code owns archive validation, ordering and bounded decompression.
 */
@Component
public final class CbzReader {
  /** Returns exact non-directory entry names from a validated archive. */
  public Set<String> entries(Path file) throws IOException {
    try (var archive = new CbzArchiveReader(Files.newByteChannel(file))) {
      return archive.entries();
    }
  }

  /** Returns image entries in natural filename order when no document supplies a manifest. */
  public List<String> pages(Path file) throws IOException {
    try (var archive = new CbzArchiveReader(Files.newByteChannel(file))) {
      return archive.pages();
    }
  }

  /** Reads one exact image entry with the shared decompression limit. */
  public byte[] read(Path file, String entryName) throws IOException {
    try (var archive = new CbzArchiveReader(Files.newByteChannel(file))) {
      return archive.read(entryName);
    }
  }
}
