package com.petitesroues.sync;

import com.petitesroues.media.MediaFingerprint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The fingerprints a {@code TitleFile} names: the media's {@code v1:} fingerprint ({@link
 * MediaFingerprint}, which reads only the ends of a large file), or {@code sha256:} over every byte
 * for the small files the desktop generates or keeps in Git (documents, pages).
 */
public final class FileFingerprints {
  private static final String SHA256 = "sha256:";

  private FileFingerprints() {}

  /** {@code sha256:} and the hex digest of {@code bytes}. */
  public static String sha256(byte[] bytes) {
    return SHA256 + HexFormat.of().formatHex(digest().digest(bytes));
  }

  /**
   * Whether the file read from {@code channel} has {@code fingerprint}.
   *
   * @throws IllegalArgumentException for a fingerprint of an unknown kind
   */
  public static boolean matches(String fingerprint, SeekableByteChannel channel)
      throws IOException {
    if (fingerprint.startsWith(SHA256)) {
      MessageDigest digest = digest();
      ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
      channel.position(0);
      while (channel.read(buffer) > 0) {
        digest.update(buffer.flip());
        buffer.clear();
      }
      return fingerprint.equals(SHA256 + HexFormat.of().formatHex(digest.digest()));
    }
    if (fingerprint.startsWith("v1:")) return fingerprint.equals(MediaFingerprint.of(channel));
    throw new IllegalArgumentException("Unknown fingerprint: " + fingerprint);
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Every Java runtime provides SHA-256", impossible);
    }
  }
}
