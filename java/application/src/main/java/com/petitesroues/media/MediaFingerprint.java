package com.petitesroues.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the v1 fingerprint that a document's reference records for its video or CBZ: {@code v1:}
 * and the lowercase hex SHA-256 of the file size as 8 bytes big-endian, then the first 4 MiB, then
 * the last 4 MiB. A file of at most 8 MiB is hashed once, whole, after its size.
 *
 * <p>Reading only the ends keeps a multi-gigabyte video fast to check while still telling apart
 * re-encodes and different rips: MP4 sample tables sit at one end, and a CBZ's central directory,
 * with every entry's CRC, sits at the end. {@code prepare/reference.py} implements the same rule.
 */
public final class MediaFingerprint {
  static final int END = 4 * 1024 * 1024;

  private MediaFingerprint() {}

  public static String of(Path file) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Every Java runtime provides SHA-256", impossible);
    }
    try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
      long size = channel.size();
      digest.update(ByteBuffer.allocate(Long.BYTES).putLong(size).flip());
      if (size <= 2L * END) read(channel, 0, size, digest);
      else {
        read(channel, 0, END, digest);
        read(channel, size - END, END, digest);
      }
    }
    return "v1:" + HexFormat.of().formatHex(digest.digest());
  }

  private static void read(FileChannel channel, long from, long length, MessageDigest digest)
      throws IOException {
    var buffer = ByteBuffer.allocate((int) length);
    while (buffer.hasRemaining())
      if (channel.read(buffer, from + buffer.position()) < 0)
        throw new IOException("File shrank while it was being fingerprinted");
    digest.update(buffer.flip());
  }
}
