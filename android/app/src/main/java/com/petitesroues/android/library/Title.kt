package com.petitesroues.android.library

import android.net.Uri

enum class TitleKind {
  VIDEO,
  COMIC,
}

/**
 * One indexed title: a study document and the video or CBZ its reference fingerprint names, from
 * any content folder ([com.petitesroues.library.linking.MediaLinks]). [path] identifies the title and keys its progress: the document's
 * place in its content folder, prefixed with that folder's name ([TitlePaths]).
 *
 * The library groups titles into shelves from the document's [series], [season], [episode] and
 * [volume]. [poster] and [seriesPoster] live with the media, found by the shared rules
 * ([com.petitesroues.library.discovery.ContentScanner]); [firstPage] is a comic's first manifest
 * page, its card image when it has no poster.
 */
data class Title(
  val path: String,
  override val name: String,
  override val series: String?,
  override val kind: TitleKind,
  val documentUri: Uri,
  val mediaUri: Uri?,
  override val documentId: String,
  val durationSeconds: Double?,
  override val season: Int?,
  override val episode: Int?,
  override val volume: Int?,
  val poster: Uri?,
  val seriesPoster: Uri?,
  val firstPage: String?,
) : Shelvable {
  /** The content folder the title came from. */
  val folder: String
    get() = path.substringBefore('/')

  override val mediaAvailable: Boolean
    get() = mediaUri != null

  override val hasTitleArt: Boolean
    get() = poster != null || (kind == TitleKind.COMIC && mediaUri != null && firstPage != null)

  override val hasSeriesPoster: Boolean
    get() = seriesPoster != null
}

/**
 * Where a title was left: a video position or a comic page, and how far through the title that is
 * from 0 to 1 ([fraction] is null for progress saved before fractions were, or a video whose
 * duration was not yet known).
 */
data class Progress(val positionSeconds: Double?, val pageId: String?, val fraction: Double? = null)
