package app.petitesroues.android.library

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.petitesroues.library.discovery.ContentTree
import java.nio.channels.SeekableByteChannel

/**
 * A content folder granted through the Storage Access Framework, as the shared [ContentTree] the
 * desktop's rules walk ([app.petitesroues.library.discovery.ContentScanner]). Handles are document
 * URIs beneath [tree]. Each folder costs one provider query, which is why the scanner skips hidden
 * folders and `content-archive` before descending.
 */
class SafContentTree(private val resolver: ContentResolver, private val tree: Uri) : ContentFolder {
  private val rootId = DocumentsContract.getTreeDocumentId(tree)

  override fun root(): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

  /**
   * Lists one folder. Fails when Android cannot list it, or lists the content folder itself as
   * empty: the storage provider answers that way when it cannot read storage (it logs the folder as
   * "hidden"), and an empty result must not be mistaken for a library with nothing in it.
   */
  override fun children(folder: Uri): List<ContentTree.Entry<Uri>> {
    val id = DocumentsContract.getDocumentId(folder)
    val listing =
      resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
        arrayOf(
          Document.COLUMN_DOCUMENT_ID,
          Document.COLUMN_DISPLAY_NAME,
          Document.COLUMN_MIME_TYPE,
          Document.COLUMN_SIZE,
          Document.COLUMN_LAST_MODIFIED,
        ),
        null,
        null,
        null,
      ) ?: error("Android could not list a folder in ${name()}")
    return listing.use { cursor ->
      if (id == rootId && cursor.count == 0)
        error("Android listed the folder as empty; it may be unreadable until the phone restarts")
      buildList {
        while (cursor.moveToNext()) {
          val folder = cursor.getString(2) == Document.MIME_TYPE_DIR
          add(
            ContentTree.Entry(
              cursor.getString(1),
              DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
              folder,
              if (folder || cursor.isNull(3)) 0 else cursor.getLong(3),
              if (cursor.isNull(4)) 0 else cursor.getLong(4),
            )
          )
        }
      }
    }
  }

  override fun open(file: Uri): SeekableByteChannel = open(resolver, file)

  override fun name(): String =
    resolver.query(root(), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    } ?: error("Cannot open $tree")

  companion object {
    /** Opens a document for reading at any offset; closing the channel closes the descriptor. */
    fun open(resolver: ContentResolver, file: Uri): SeekableByteChannel =
      ParcelFileDescriptor.AutoCloseInputStream(
          resolver.openFileDescriptor(file, "r") ?: error("Cannot open $file")
        )
        .channel
  }
}
