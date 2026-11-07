package com.petitesroues.android.library

import android.net.Uri

enum class TitleKind {
  VIDEO,
  COMIC,
}

/**
 * One indexed title: a `document.json` and its single sibling video or CBZ inside the content
 * folder. [path] is the title folder relative to the content folder, prefixed with that folder's
 * name so titles from several content folders stay distinct, and identifies the title.
 *
 * The library groups titles into shelves from the document's [series], [season], [episode] and
 * [volume]. [poster] is a `poster.*` beside the document and [seriesPoster] the nearest one in a
 * folder above it (never the content folder itself); [firstPage] is a comic's first manifest page,
 * its card image when it has no poster.
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
