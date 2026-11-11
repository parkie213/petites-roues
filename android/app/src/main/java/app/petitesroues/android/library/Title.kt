package app.petitesroues.android.library

import android.net.Uri
import app.petitesroues.library.discovery.MediaKind
import app.petitesroues.library.shelves.Shelvable

enum class TitleKind {
  VIDEO,
  COMIC,
}

/**
 * One library entry: a study document and the video or CBZ its reference fingerprint names, from
 * any content folder ([app.petitesroues.library.linking.MediaLinks]), or a video or CBZ that no
 * document references yet ([documentUri] null), named from its path
 * ([app.petitesroues.library.shelves.MediaNames]). [path] identifies the entry: the document's
 * place in its content folder ([TitlePaths]), or the media's, prefixed with that folder's name. Its
 * progress is saved under [progressKey].
 *
 * The library groups entries into shelves by the rules it shares with the desktop
 * ([app.petitesroues.library.shelves.Shelves]): from the document's [series], [season], [episode],
 * [volume], [shelf] and [sourceLanguage]. [poster] and [seriesPoster] live with the media, found by
 * the shared rules ([app.petitesroues.library.discovery.ContentScanner]); [firstPage] is a comic's
 * first manifest page, its card image when it has no poster (else the CBZ's first image).
 * [mediaPath] is the media's path within its content folder. Each refresh rebuilds the index of
 * them ([app.petitesroues.android.data.IndexDatabase]).
 */
data class Title(
  val path: String,
  val name: String,
  val series: String?,
  val kind: TitleKind,
  val documentUri: Uri?,
  val mediaUri: Uri?,
  val documentId: String,
  val durationSeconds: Double?,
  val season: Int?,
  val episode: Int?,
  val volume: Int?,
  val poster: Uri?,
  val seriesPoster: Uri?,
  val firstPage: String?,
  val shelf: String? = null,
  val sourceLanguage: String? = null,
  val mediaPath: String? = null,
) : Shelvable {
  /**
   * What the title's reading or playback progress is saved under: its document's ID, which moving
   * or renaming files does not change, so a sync that lays files out differently keeps it. Media
   * without a document has a path-based ID.
   */
  val progressKey: String
    get() = documentId

  /**
   * A comic with no study document: it reads from a pages-only document built from its CBZ, in the
   * direction chosen for it.
   */
  val pagesOnly: Boolean
    get() = documentUri == null && kind == TitleKind.COMIC

  /** The content folder the title came from. */
  val folder: String
    get() = path.substringBefore('/')

  override fun kind(): MediaKind = if (kind == TitleKind.COMIC) MediaKind.COMIC else MediaKind.VIDEO

  override fun documentId(): String = documentId

  override fun title(): String = name

  override fun series(): String? = series

  override fun season(): Int? = season

  override fun episode(): Int? = episode

  override fun volume(): Int? = volume

  override fun shelf(): String? = shelf

  override fun sourceLanguage(): String? = sourceLanguage

  override fun documentAvailable(): Boolean = documentUri != null

  override fun mediaAvailable(): Boolean = mediaUri != null

  override fun hasTitleArt(): Boolean =
    poster != null || (kind == TitleKind.COMIC && mediaUri != null)

  override fun hasSeriesPoster(): Boolean = seriesPoster != null

  override fun mediaPath(): String? = mediaPath
}
