package com.petitesroues.android.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * Discovers title folders beneath a content folder granted through the Storage Access Framework.
 * Runs only on an explicit refresh; never reads documents or touches the index. Hidden folders
 * (`.git`, Syncthing's `.stversions`, …) and `content-archive` (archived preparation runs, which
 * hold old document copies) are skipped, as the desktop scanner does: they are not live titles,
 * and walking them one SAF query per folder is what made refreshes slow.
 */
class TitleScanner(private val resolver: ContentResolver) {
  /** Total size of the files seen by [scan] so far (skipped folders excluded), in bytes. */
  var bytes = 0L
    private set

  /**
   * A folder containing `document.json`, with the names and URIs of its other files, its own
   * `poster.*` and the nearest one in a folder above it, excluding the content folder itself.
   */
  data class Found(
    val path: String,
    val document: Uri,
    val siblings: Map<String, Uri>,
    val poster: Uri? = null,
    val seriesPoster: Uri? = null,
  )

  /** The display name of the content folder [tree] itself. */
  fun name(tree: Uri): String {
    val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return resolver.query(folder, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    } ?: error("Cannot open $tree")
  }

  /**
   * Every title folder beneath [tree], sorted by relative path. Fails when Android cannot list a
   * folder, or lists the content folder itself as empty: the storage provider answers that way
   * when it cannot read storage (it logs the folder as "hidden"), and an empty result must not be
   * mistaken for a library with no titles.
   */
  fun scan(tree: Uri): List<Found> {
    val found = mutableListOf<Found>()
    walk(tree, DocumentsContract.getTreeDocumentId(tree), "", null, found)
    return found.sortedBy { it.path }
  }

  private fun walk(tree: Uri, folderId: String, path: String, above: Uri?, found: MutableList<Found>) {
    val files = mutableMapOf<String, Uri>()
    val folders = mutableListOf<Pair<String, String>>()
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
    val listing =
      resolver.query(
        children,
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE),
        null,
        null,
        null,
      ) ?: error("Android could not list ${path.ifEmpty { "the folder" }}")
    listing.use { cursor ->
      if (path.isEmpty() && cursor.count == 0)
        error("Android listed the folder as empty; it may be unreadable until the phone restarts")
      while (cursor.moveToNext()) {
        val id = cursor.getString(0)
        val name = cursor.getString(1)
        if (name.startsWith(".")) continue
        if (cursor.getString(2) == Document.MIME_TYPE_DIR) {
          if (name != ARCHIVE) folders += id to name
        } else {
          files[name] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
          if (!cursor.isNull(3)) bytes += cursor.getLong(3)
        }
      }
    }
    val poster = files.entries.firstOrNull { POSTER.matches(it.key) }?.value
    files.remove(DOCUMENT)?.let { found += Found(path, it, files, poster, above) }
    val below = if (path.isEmpty()) null else poster ?: above
    for ((id, name) in folders) walk(tree, id, if (path.isEmpty()) name else "$path/$name", below, found)
  }

  private companion object {
    const val DOCUMENT = "document.json"
    const val ARCHIVE = "content-archive"
    val POSTER = Regex("poster\\.(jpe?g|png|webp)", RegexOption.IGNORE_CASE)
  }
}
