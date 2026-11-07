package com.petitesroues.android.library

import android.content.ContentResolver
import android.net.Uri
import com.petitesroues.documents.StudyDocumentReader
import com.petitesroues.library.discovery.ContentScanner
import com.petitesroues.library.discovery.MediaKind
import com.petitesroues.library.linking.MediaLinks
import com.petitesroues.media.MediaFingerprint
import com.petitesroues.model.document.ComicDocument
import com.petitesroues.model.document.StudyDocument
import com.petitesroues.model.document.VideoDocument

/**
 * Builds the phone's library from the chosen content folders and opens title documents. Refresh is
 * the only operation that scans the folders; browsing reads the index.
 *
 * Discovery uses the desktop's rules ([ContentScanner]): every study document and every video or
 * CBZ in any chosen folder, with posters found beside the media. A document links to media whose
 * fingerprint its reference records, in any folder, by the shared rule ([MediaLinks]).
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

  /** One chosen folder's walk, named by the folder. */
  private class Scanned(val root: String, val discovery: ContentScanner.Discovery<Uri>)


  /**
   * Rescans [trees], replaces the index with every readable title and reports the rest, with the
   * folders' total size. Titles are keyed under their content folder's name, so a second folder
   * with the same name is skipped. A folder that cannot be scanned keeps the titles it had, so a
   * storage failure is reported instead of emptying the library. Media is fingerprinted only when
   * new or changed since the last refresh (8 MiB read per file).
   */
  fun refresh(trees: List<Uri>): Refreshed {
    val titles = mutableListOf<Title>()
    val problems = mutableListOf<Problem>()
    val scanned = mutableListOf<Scanned>()
    val roots = mutableSetOf<String>()
    var bytes = 0L
    var measured = true
    val previous by lazy { store.titles() }
    for (tree in trees) {
      val folder = SafContentTree(resolver, tree)
      try {
        val root = folder.name()
        if (!roots.add(root)) {
          problems += Problem(root, "Another content folder is also named $root")
          continue
        }
        val discovery = ContentScanner.scan(folder)
        scanned += Scanned(root, discovery)
        bytes += discovery.bytes()
      } catch (failure: Exception) {
        // Titles' document URIs are built beneath their folder's tree URI.
        val kept = previous.filter { it.documentUri.toString().startsWith("$tree/") }
        val name = kept.firstOrNull()?.folder ?: tree.lastPathSegment ?: tree.toString()
        val keeping = if (kept.isEmpty()) "" else " (kept its ${kept.size} titles from the last refresh)"
        problems += Problem(name, (failure.message ?: failure.toString()) + keeping)
        titles += kept
        measured = false
      }
    }
    val byKey = MediaLinks.byKey(fingerprint(scanned, problems))
    for (folder in scanned)
      for (found in folder.discovery.documents()) index(folder.root, found, byKey, problems)?.let(titles::add)
    store.replaceTitles(titles)
    return Refreshed(problems, bytes.takeIf { measured })
  }

  /**
   * Fingerprints every media file, reusing cached fingerprints for unchanged files, and replaces
   * the cache with this refresh's files. A file that cannot be read is reported and left out.
   * Candidates are in folder and path order, as [MediaLinks] expects.
   */
  private fun fingerprint(
    scanned: List<Scanned>,
    problems: MutableList<Problem>,
  ): List<MediaLinks.Candidate<ContentScanner.Media<Uri>>> {
    val cache = store.fingerprints()
    val result = mutableListOf<MediaLinks.Candidate<ContentScanner.Media<Uri>>>()
    val seen = mutableMapOf<String, LibraryStore.Fingerprint>()
    for (folder in scanned)
      for (media in folder.discovery.media()) {
        val path = "${folder.root}/${media.relativePath()}"
        media.posterProblem()?.let { problems += Problem(path, it) }
        try {
          val uri = media.handle().toString()
          val cached = cache[uri]
          val fingerprint =
            if (cached != null && media.unchangedSince(cached.size, cached.modifiedMillis)) cached.fingerprint
            else SafContentTree.open(resolver, media.handle()).use(MediaFingerprint::of)
          seen[uri] = LibraryStore.Fingerprint(media.size(), media.modifiedMillis(), fingerprint)
          result += MediaLinks.Candidate(media, media.kind(), fingerprint, folder.root)
        } catch (failure: Exception) {
          problems += Problem(path, failure.message ?: failure.toString())
        }
      }
    store.replaceFingerprints(seen)
    return result
  }

  /** Reads one candidate document into a title linked to its media, or records why it cannot. */
  private fun index(
    root: String,
    found: ContentScanner.Found<Uri>,
    media: Map<String, List<MediaLinks.Candidate<ContentScanner.Media<Uri>>>>,
    problems: MutableList<Problem>,
  ): Title? {
    val path = TitlePaths.of(root, found.relativePath())
    return try {
      val bytes = bytes(found.handle())
      if (!reader.isStudyDocument(bytes)) return null
      val document = reader.decode(bytes)
      val video = document.body() as? VideoDocument
      val comic = document.body() as? ComicDocument
      val kind = if (comic != null) TitleKind.COMIC else TitleKind.VIDEO
      val reference = video?.reference()?.fingerprint() ?: comic?.reference()?.fingerprint()
      val mediaKind = if (comic != null) MediaKind.COMIC else MediaKind.VIDEO
      val candidates = MediaLinks.key(mediaKind, reference)?.let(media::get).orEmpty()
      val linked = MediaLinks.choose(mediaKind, reference, root, candidates).orElse(null)
      Title(
        path = path,
        name = document.title().ifBlank { path.substringAfterLast('/') },
        series = document.series(),
        kind = kind,
        documentUri = found.handle(),
        mediaUri = linked?.handle(),
        documentId = document.id(),
        durationSeconds = video?.reference()?.durationSeconds(),
        season = video?.season(),
        episode = video?.episode(),
        volume = comic?.volume(),
        poster = linked?.poster()?.handle(),
        seriesPoster = linked?.seriesPoster()?.handle(),
        firstPage = comic?.pages()?.firstOrNull()?.entry(),
      )
    } catch (failure: Exception) {
      problems += Problem(path, failure.message ?: failure.toString())
      null
    }
  }

  /** Reads and validates a title's document exactly as the desktop server does. */
  fun read(document: Uri): StudyDocument = reader.decode(bytes(document))

  private fun bytes(document: Uri): ByteArray =
    resolver.openInputStream(document)?.use { it.readBytes() } ?: error("Cannot open $document")
}
