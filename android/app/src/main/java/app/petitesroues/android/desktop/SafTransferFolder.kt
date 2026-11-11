package app.petitesroues.android.desktop

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.petitesroues.android.library.AppFolder
import app.petitesroues.sync.PathTransferFolder
import app.petitesroues.sync.TransferFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/** The shared download code's view of [tree]: plain files for the app's own folder, else SAF. */
fun transferFolder(resolver: ContentResolver, tree: Uri): TransferFolder =
  if (AppFolder.isFile(tree)) PathTransferFolder(File(checkNotNull(tree.path)).toPath())
  else SafTransferFolder(resolver, tree)

/**
 * A download folder's name for people: the app's own folder's, or the chosen folder's path on its
 * volume.
 */
fun folderLabel(tree: Uri): String =
  if (AppFolder.isFile(tree)) AppFolder.LABEL
  else DocumentsContract.getTreeDocumentId(tree).let { id -> id.substringAfter(':').ifEmpty { id } }

/**
 * The download folder as a Storage Access Framework tree the app may write to: the shared download
 * code's [TransferFolder]. Folders are created as needed and remembered for the folder's lifetime;
 * files are looked up by display name each time, since parts come and go.
 */
class SafTransferFolder(private val resolver: ContentResolver, private val tree: Uri) :
  TransferFolder {
  private val rootId = DocumentsContract.getTreeDocumentId(tree)
  private val folders = HashMap<List<String>, String>()

  private class Child(val id: String, val size: Long, val folder: Boolean)

  private fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

  private fun children(parentId: String): Map<String, Child> {
    val columns =
      arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE,
      )
    val found = HashMap<String, Child>()
    resolver
      .query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
        columns,
        null,
        null,
        null,
      )
      ?.use { cursor ->
        while (cursor.moveToNext()) found[cursor.getString(1)] =
          Child(
            cursor.getString(0),
            if (cursor.isNull(2)) 0 else cursor.getLong(2),
            cursor.getString(3) == Document.MIME_TYPE_DIR,
          )
      }
    return found
  }

  /** The folder at [path], created when [create] is set; null when absent and not created. */
  private fun folder(path: List<String>, create: Boolean): String? {
    folders[path]?.let {
      return it
    }
    var id = rootId
    for (depth in path.indices) {
      val prefix = path.subList(0, depth + 1)
      val known = folders[prefix]
      id =
        known
          ?: children(id)[path[depth]]?.takeIf { it.folder }?.id
          ?: if (!create) return null
          else
            DocumentsContract.createDocument(resolver, uri(id), Document.MIME_TYPE_DIR, path[depth])
              ?.let(DocumentsContract::getDocumentId)
              ?: throw IOException("Cannot create the folder ${prefix.joinToString("/")}")
      folders[prefix.toList()] = id
    }
    return id
  }

  private fun file(path: List<String>, name: String): Child? =
    folder(path.dropLast(1), false)?.let { children(it)[name] }?.takeIf { !it.folder }

  private fun read(child: Child?, path: List<String>): SeekableByteChannel {
    val found = child ?: throw IOException("${path.last()} is missing")
    val descriptor =
      resolver.openFileDescriptor(uri(found.id), "r")
        ?: throw IOException("Cannot open ${path.last()}")
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel
  }

  private fun partName(path: List<String>) = TransferFolder.partName(path.last())

  override fun size(path: List<String>): Long = file(path, path.last())?.size ?: -1

  override fun open(path: List<String>): SeekableByteChannel = read(file(path, path.last()), path)

  override fun partSize(path: List<String>): Long = file(path, partName(path))?.size ?: -1

  override fun appendPart(path: List<String>): OutputStream {
    val parent = folder(path.dropLast(1), true)!!
    val part =
      children(parent)[partName(path)]?.id
        ?: DocumentsContract.createDocument(
            resolver,
            uri(parent),
            "application/octet-stream",
            partName(path),
          )
          ?.let(DocumentsContract::getDocumentId)
        ?: throw IOException("Cannot create ${partName(path)}")
    return resolver.openOutputStream(uri(part), "wa")
      ?: throw IOException("Cannot write ${partName(path)}")
  }

  override fun openPart(path: List<String>): SeekableByteChannel =
    read(file(path, partName(path)), path)

  override fun deletePart(path: List<String>) {
    file(path, partName(path))?.let { DocumentsContract.deleteDocument(resolver, uri(it.id)) }
  }

  override fun commit(path: List<String>) {
    val parent =
      folder(path.dropLast(1), false) ?: throw IOException("${path.last()} was not downloaded")
    val existing = children(parent)
    val part = existing[partName(path)] ?: throw IOException("${path.last()} was not downloaded")
    existing[path.last()]?.let { DocumentsContract.deleteDocument(resolver, uri(it.id)) }
    DocumentsContract.renameDocument(resolver, uri(part.id), path.last())
      ?: throw IOException("Cannot rename ${path.last()}")
  }
}
