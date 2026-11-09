package com.petitesroues.android.pages

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.petitesroues.android.library.AppFolder
import java.io.File
import android.provider.DocumentsContract.Document

/**
 * Finds the Markdown pages in content folders granted through the Storage Access Framework: the
 * `*.md` files directly inside a named folder ([MarkdownPage.QUIZZES], [MarkdownPage.NOTES]) at
 * each content folder's root. The folder name says what they are, so other Markdown is never
 * listed. The files are small, so their text is read here.
 */
class MarkdownPageScanner(private val resolver: ContentResolver) {
  /** Every page in [trees]' [folder], in content-folder order and then by file name; unreadable folders are skipped. */
  fun scan(trees: List<Uri>, folder: String): List<MarkdownPage> =
    trees.flatMap { tree ->
      runCatching { if (AppFolder.isFile(tree)) scanFiles(tree, folder) else scan(tree, folder) }.getOrDefault(emptyList())
    }

  /**
   * The app's own folder holds titles copied from the desktop, and one subfolder per desktop
   * repository with its own `notes/` and `quizzes/`.
   */
  private fun scanFiles(tree: Uri, folder: String): List<MarkdownPage> =
    File(checkNotNull(tree.path)).listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }.flatMap { repository ->
      File(repository, folder).listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".md") && !it.name.startsWith(".") }
        .sortedBy { it.name }
        .map { file ->
          val id = file.name.removeSuffix(".md")
          val text = file.readText()
          MarkdownPage("${tree.path}/${repository.name}/$folder/$id", repository.name, MarkdownPage.title(text, id), text)
        }
    }

  private fun scan(tree: Uri, folder: String): List<MarkdownPage> {
    val root = DocumentsContract.getTreeDocumentId(tree)
    val pages = children(tree, root).firstOrNull { it.folder && it.name == folder } ?: return emptyList()
    val name = name(tree, root) ?: root
    return children(tree, pages.id)
      .filter { !it.folder && it.name.endsWith(".md") && !it.name.startsWith(".") }
      .sortedBy { it.name }
      .map { file ->
        val id = file.name.removeSuffix(".md")
        val text =
          resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, file.id))!!.bufferedReader().use { it.readText() }
        MarkdownPage("$root/$folder/$id", name, MarkdownPage.title(text, id), text)
      }
  }

  private data class Child(val id: String, val name: String, val folder: Boolean)

  private fun children(tree: Uri, folderId: String): List<Child> =
    resolver
      .query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId),
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
        null,
        null,
        null,
      )
      ?.use { cursor ->
        buildList {
          while (cursor.moveToNext())
            add(Child(cursor.getString(0), cursor.getString(1), cursor.getString(2) == Document.MIME_TYPE_DIR))
        }
      } ?: emptyList()

  private fun name(tree: Uri, id: String): String? =
    resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    }
}
