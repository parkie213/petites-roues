package com.petitesroues.android.library

import android.net.Uri
import com.petitesroues.library.discovery.MediaKind
import com.petitesroues.library.shelves.Shelvable

enum class TitleKind {
  VIDEO,
  COMIC,
}

/**
 * One library entry: a study document and the video or CBZ its reference fingerprint names, from
 * any content folder ([com.petitesroues.library.linking.MediaLinks]), or a video or CBZ that no
 * document references yet ([documentUri] null), named from its path
 * ([com.petitesroues.library.shelves.MediaNames]). [path] identifies the entry and keys its
 * progress: the document's place in its content folder ([TitlePaths]), or the media's, prefixed
 * with that folder's name.
 *
 * The library groups entries into shelves by the rules it shares with the desktop
 * ([com.petitesroues.library.shelves.Shelves]): from the document's [series], [season], [episode],
 * [volume], [shelf] and [sourceLanguage]. [poster] and [seriesPoster] live with the media, found by
 * the shared rules ([com.petitesroues.library.discovery.ContentScanner]); [firstPage] is a comic's
 * first manifest page, its card image when it has no poster (else the CBZ's first image).
 * [mediaPath] is the media's path within its content folder.
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

  override fun hasTitleArt(): Boolean = poster != null || (kind == TitleKind.COMIC && mediaUri != null)

  override fun hasSeriesPoster(): Boolean = seriesPoster != null

  override fun mediaPath(): String? = mediaPath
}

/**
 * Where a title was left: a video position or a comic page, and how far through the title that is
 * from 0 to 1 ([fraction] is null for progress saved before fractions were, or a video whose
 * duration was not yet known).
 */
data class Progress(val positionSeconds: Double?, val pageId: String?, val fraction: Double? = null)
