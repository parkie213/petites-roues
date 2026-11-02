package com.petitesroues.comic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * Reads CBZ entries without extracting files. Rejects ambiguous archives and bounds decompression;
 * callers select entries using the validated document manifest, never arbitrary request paths.
 */
@Component
public final class CbzReader {
  private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

  /** Returns exact non-directory entry names, rejecting duplicates and oversized archives. */
  public Set<String> entries(Path file) throws IOException {
    try (var zip = new ZipFile(file.toFile())) {
      if (zip.size() > 20000) throw new IllegalArgumentException("CBZ has too many entries");
      Set<String> names = new HashSet<>();
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (!entry.isDirectory() && !names.add(entry.getName()))
          throw new IllegalArgumentException("Duplicate CBZ entry: " + entry.getName());
      }
      return names;
    }
  }

  /** Reads one image with a strict uncompressed size limit and closes the archive before return. */
  public byte[] read(Path file, String entryName) throws IOException {
    try (var zip = new ZipFile(file.toFile())) {
      var entry = zip.getEntry(entryName);
      if (entry == null || entry.isDirectory())
        throw new IllegalArgumentException("CBZ page is missing: " + entryName);
      if (entry.getSize() > MAX_IMAGE_BYTES)
        throw new IllegalArgumentException("CBZ page exceeds 32 MiB");
      try (var input = zip.getInputStream(entry)) {
        var bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
        if (bytes.length > MAX_IMAGE_BYTES)
          throw new IllegalArgumentException("CBZ page exceeds 32 MiB");
        return bytes;
      }
    }
  }
}
