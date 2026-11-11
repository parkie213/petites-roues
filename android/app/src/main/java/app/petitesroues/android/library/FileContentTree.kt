package app.petitesroues.android.library

import android.net.Uri
import app.petitesroues.library.discovery.ContentTree
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * A content folder reached with plain file access ([AppFolder]), as the shared [ContentTree].
 * Handles are `file:` URIs, which the content resolver, the players and the readers open like the
 * SAF folders' document URIs. Symbolic links are left out, as the desktop's tree leaves them out.
 */
class FileContentTree(private val tree: Uri) : ContentFolder {
  private val root = File(checkNotNull(tree.path) { "Not a file folder: $tree" })

  override fun root(): Uri = tree

  override fun children(folder: Uri): List<ContentTree.Entry<Uri>> {
    val files =
      File(checkNotNull(folder.path)).listFiles() ?: throw IOException("Cannot list ${folder.path}")
    return files
      .filter { !Files.isSymbolicLink(it.toPath()) && (it.isFile || it.isDirectory) }
      .map {
        ContentTree.Entry(
          it.name,
          Uri.fromFile(it),
          it.isDirectory,
          if (it.isFile) it.length() else 0,
          it.lastModified(),
        )
      }
  }

  override fun open(file: Uri): SeekableByteChannel =
    FileChannel.open(File(checkNotNull(file.path)).toPath(), StandardOpenOption.READ)

  override fun name(): String = root.name
}
