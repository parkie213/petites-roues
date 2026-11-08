package com.petitesroues.sync;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.List;

/**
 * The phone's download folder, as the shared download code sees it: java.nio in tests and on the
 * desktop, the Storage Access Framework on Android. Paths are a {@code TitleFile}'s segments.
 *
 * <p>A file is first written to a hidden part beside it, which the library scan ignores, and only
 * {@link #commit committed} into place once complete and verified. A part that is left over after a
 * cut or a killed app is resumed from its size.
 */
public interface TransferFolder {
  /** The finished file's size, or -1 if there is none. */
  long size(List<String> path) throws IOException;

  /** Reads the finished file, to check its fingerprint. */
  SeekableByteChannel open(List<String> path) throws IOException;

  /** The part's size, or -1 if there is none. */
  long partSize(List<String> path) throws IOException;

  /** Appends to the part, creating it and its folders if needed. */
  OutputStream appendPart(List<String> path) throws IOException;

  /** Reads the part, to check its fingerprint before committing it. */
  SeekableByteChannel openPart(List<String> path) throws IOException;

  /** Deletes the part, if any. */
  void deletePart(List<String> path) throws IOException;

  /** Moves the part into place, replacing any finished file. */
  void commit(List<String> path) throws IOException;

  /** The part's name for a file name: hidden, so the library scan skips it. */
  static String partName(String name) {
    return "." + name + ".part";
  }
}
