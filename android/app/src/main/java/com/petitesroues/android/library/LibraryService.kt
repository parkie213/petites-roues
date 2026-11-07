package com.petitesroues.android.library

import android.content.ContentResolver
import android.net.Uri
import com.petitesroues.documents.StudyDocumentReader
import com.petitesroues.model.document.ComicDocument
import com.petitesroues.model.document.StudyDocument
import com.petitesroues.model.document.VideoDocument

/**
 * Builds the phone's library from the chosen content folders and opens title documents. Refresh is
 * the only operation that scans the folders; browsing reads the index.
 */
class LibraryService(
  private val resolver: ContentResolver,
  private val store: LibraryStore,
  private val reader: StudyDocumentReader = StudyDocumentReader(),
) {
  /** Titles that could not be indexed, with the reason, for the refresh summary. */
  data class Problem(val path: String, val message: String)

  /**
   * What a refresh found: titles that could not be indexed, and the folders' total size in bytes,
   * or null when a folder could not be scanned and the total would be too low.
   */
  data class Refreshed(val problems: List<Problem>, val bytes: Long?)

  /**
   * Rescans [trees], replaces the index with every readable title and reports the rest, with the
   * folders' total size. Titles are keyed under their content folder's name, so a second folder
   * with the same name is skipped. A folder that cannot be scanned keeps the titles it had, so a
   * storage failure is reported instead of emptying the library.
   */
  fun refresh(trees: List<Uri>): Refreshed {
    val titles = mutableListOf<Title>()
    val problems = mutableListOf<Problem>()
    val scanner = TitleScanner(resolver)
    val roots = mutableSetOf<String>()
    var measured = true
    val previous by lazy { store.titles() }
    for (tree in trees) {
      val found =
        try {
          val root = scanner.name(tree)
          if (!roots.add(root)) {
            problems += Problem(root, "Another content folder is also named $root")
            continue
          }
          scanner.scan(tree).map { it.copy(path = if (it.path.isEmpty()) root else "$root/${it.path}") }
        } catch (failure: Exception) {
          // Titles' document URIs are built beneath their folder's tree URI.
          val kept = previous.filter { it.documentUri.toString().startsWith("$tree/") }
          val folder = kept.firstOrNull()?.folder ?: tree.lastPathSegment ?: tree.toString()
          val keeping = if (kept.isEmpty()) "" else " (kept its ${kept.size} titles from the last refresh)"
          problems += Problem(folder, (failure.message ?: failure.toString()) + keeping)
          titles += kept
          measured = false
          continue
        }
      titles += found.mapNotNull { index(it, problems) }
    }
    store.replaceTitles(titles)
    return Refreshed(problems, scanner.bytes.takeIf { measured })
  }

  /** Reads one found title folder into a title, or records why it cannot be indexed. */
  private fun index(found: TitleScanner.Found, problems: MutableList<Problem>): Title? =
    try {
      val document = read(found.document)
      val video = document.body() as? VideoDocument
      val comic = document.body() as? ComicDocument
      val kind = if (comic != null) TitleKind.COMIC else TitleKind.VIDEO
      val media = TitleMedia.select(found.siblings.keys.toList(), kind)
      Title(
        path = found.path,
        name = document.title().ifBlank { found.path.substringAfterLast('/') },
        series = document.series(),
        kind = kind,
        documentUri = found.document,
        mediaUri = media?.let(found.siblings::get),
        documentId = document.id(),
        durationSeconds = video?.reference()?.durationSeconds(),
        season = video?.season(),
        episode = video?.episode(),
        volume = comic?.volume(),
        poster = found.poster,
        seriesPoster = found.seriesPoster,
        firstPage = comic?.pages()?.firstOrNull()?.entry(),
      )
    } catch (failure: Exception) {
      problems += Problem(found.path, failure.message ?: failure.toString())
      null
    }

  /** Reads and validates a title's document exactly as the desktop server does. */
  fun read(document: Uri): StudyDocument {
    val bytes =
      resolver.openInputStream(document)?.use { it.readBytes() }
        ?: error("Cannot open $document")
    return reader.decode(bytes)
  }
}
