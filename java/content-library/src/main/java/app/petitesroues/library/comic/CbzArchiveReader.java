package app.petitesroues.library.comic;

import app.petitesroues.library.ordering.NaturalOrder;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * Reads CBZ entries without extracting files or decoding images. Owns the supplied seekable
 * channel, including when opening fails. Reads and closure are serialized because the archive
 * shares that channel. Platform adapters supply file access and consume the returned image bytes.
 */
public final class CbzArchiveReader implements Closeable {
  private static final int MAX_ENTRIES = 20000;
  private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
  private static final Pattern IMAGE = Pattern.compile("(?i).+\\.(jpe?g|png|webp|gif)");

  private final ZipFile zip;
  private final Set<String> entries;

  /**
   * Opens and validates an archive, rejecting more than 20,000 entries or duplicate file names.
   * Takes ownership immediately and closes the channel if parsing or validation fails.
   */
  public CbzArchiveReader(SeekableByteChannel channel) throws IOException {
    ZipFile opened = null;
    try {
      opened = ZipFile.builder().setSeekableByteChannel(channel).get();
      Set<String> names = new HashSet<>();
      var enumeration = opened.getEntries();
      int count = 0;
      while (enumeration.hasMoreElements()) {
        var entry = enumeration.nextElement();
        if (++count > MAX_ENTRIES) throw new IllegalArgumentException("CBZ has too many entries");
        if (!entry.isDirectory() && !names.add(entry.getName()))
          throw new IllegalArgumentException("Duplicate CBZ entry: " + entry.getName());
      }
      entries = Set.copyOf(names);
      zip = opened;
    } catch (IOException | RuntimeException | Error failure) {
      try {
        if (opened != null) opened.close();
        else channel.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /** Exact non-directory entry names, validated at opening and safe to retain after closure. */
  public Set<String> entries() {
    return entries;
  }

  /** Image entries in natural filename order (page-2 before page-10), excluding directories. */
  public List<String> pages() {
    return entries.stream()
        .filter(name -> IMAGE.matcher(name).matches())
        .sorted(NaturalOrder.COMPARATOR)
        .toList();
  }

  /**
   * Reads one exact non-directory entry, limiting both declared and actual decompressed data to 32
   * MiB. Does not decode the bytes or extract archive paths to disk.
   */
  public synchronized byte[] read(String entryName) throws IOException {
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

  /** Closes the archive and its owned channel after any active read completes. */
  @Override
  public synchronized void close() throws IOException {
    zip.close();
  }
}
