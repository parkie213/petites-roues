package app.petitesroues.media;

import static org.junit.jupiter.api.Assertions.*;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaFingerprintTest {
  @TempDir Path folder;

  @Test
  void anEmptyFileHashesOnlyItsSize() throws Exception {
    // SHA-256 of eight zero bytes: the size 0 as a big-endian long. prepare/reference.py agrees.
    assertEquals(
        "v1:af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc",
        MediaFingerprint.of(Files.createFile(folder.resolve("empty"))));
  }

  @Test
  void aLargeFileIsIdentifiedByItsSizeAndBothEnds() throws Exception {
    long size = 3L * MediaFingerprint.END;
    Path file = sized("large", size);
    String original = MediaFingerprint.of(file);
    assertTrue(original.matches("v1:[0-9a-f]{64}"), original);

    write(file, size / 2, (byte) 1);
    assertEquals(original, MediaFingerprint.of(file), "the middle is not read");
    write(file, size - 1, (byte) 1);
    assertNotEquals(original, MediaFingerprint.of(file), "the last 4 MiB are read");
    write(file, size - 1, (byte) 0);
    write(file, 0, (byte) 1);
    assertNotEquals(original, MediaFingerprint.of(file), "the first 4 MiB are read");
    write(file, 0, (byte) 0);
    assertEquals(original, MediaFingerprint.of(file));
    assertNotEquals(original, MediaFingerprint.of(sized("longer", size + 1)), "the size is read");
  }

  @Test
  void aFileOfAtMostEightMebibytesIsHashedWhole() throws Exception {
    long size = 2L * MediaFingerprint.END;
    Path file = sized("small", size);
    String original = MediaFingerprint.of(file);
    write(file, size / 2, (byte) 1);
    assertNotEquals(original, MediaFingerprint.of(file));
  }

  private Path sized(String name, long size) throws Exception {
    Path file = folder.resolve(name);
    try (var out = new RandomAccessFile(file.toFile(), "rw")) {
      out.setLength(size);
    }
    return file;
  }

  private static void write(Path file, long at, byte value) throws Exception {
    try (var out = new RandomAccessFile(file.toFile(), "rw")) {
      out.seek(at);
      out.write(value);
    }
  }
}
