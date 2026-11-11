package app.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.List;

/**
 * The one platform-specific part of discovery: how a registered folder's files are reached. The
 * desktop walks real paths ({@link PathContentTree}); Android walks Storage Access Framework
 * documents. {@link ContentScanner} applies the shared rules over either.
 *
 * @param <F> the platform's handle for a file or folder, such as a {@code Path} or a content URI
 */
public interface ContentTree<F> {
  /** The registered folder itself. */
  F root();

  /**
   * The files and folders directly inside {@code folder}, in any order. Symbolic links and other
   * special files are left out.
   *
   * @throws IOException if the folder cannot be listed
   */
  List<Entry<F>> children(F folder) throws IOException;

  /** Opens a file for reading at any offset, as fingerprinting needs. */
  SeekableByteChannel open(F file) throws IOException;

  /**
   * One listed child.
   *
   * @param name its file or folder name
   * @param size the file's size in bytes; 0 for a folder
   * @param modifiedMillis the file's modification time; with the size, it tells a refresh whether a
   *     media file must be fingerprinted again
   */
  record Entry<F>(String name, F handle, boolean folder, long size, long modifiedMillis) {}
}
