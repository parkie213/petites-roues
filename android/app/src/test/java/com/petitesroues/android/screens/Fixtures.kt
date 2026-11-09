package com.petitesroues.android.screens

import android.net.Uri
import com.petitesroues.android.data.Progress
import com.petitesroues.android.library.Title
import com.petitesroues.android.library.TitleKind

/**
 * A small made-up library for screenshot tests, independent of anyone's real content: a manga
 * series, a TV series with two seasons, a film and an unsorted video, with some progress.
 */
object Fixtures {
  private fun title(
    path: String,
    name: String,
    kind: TitleKind,
    series: String? = null,
    season: Int? = null,
    episode: Int? = null,
    volume: Int? = null,
    document: Boolean = true,
  ) =
    Title(
      path = "fixtures/$path",
      name = name,
      series = series,
      kind = kind,
      documentUri = if (document) Uri.parse("file:///fixtures/$path.json") else null,
      mediaUri = Uri.parse("file:///fixtures/$path.media"),
      documentId = path.substringAfterLast('/'),
      durationSeconds = if (kind == TitleKind.VIDEO) 1320.0 else null,
      season = season,
      episode = episode,
      volume = volume,
      poster = null,
      seriesPoster = null,
      firstPage = null,
      sourceLanguage = "fr",
      mediaPath = "$path.media",
    )

  val titles =
    listOf(
      title("manga/la-plume-t01", "La Plume T01", TitleKind.COMIC, series = "La Plume", volume = 1),
      title("manga/la-plume-t02", "La Plume T02", TitleKind.COMIC, series = "La Plume", volume = 2),
      title("series/le-phare-s01e01", "Le phare 1×1", TitleKind.VIDEO, "Le phare", 1, 1),
      title("series/le-phare-s01e02", "Le phare 1×2", TitleKind.VIDEO, "Le phare", 1, 2),
      title("series/le-phare-s02e01", "Le phare 2×1", TitleKind.VIDEO, "Le phare", 2, 1),
      title("films/la-riviere", "La rivière", TitleKind.VIDEO),
      title("films/holiday", "holiday", TitleKind.VIDEO, document = false),
    )

  val progress =
    mapOf(
      "la-plume-t01" to Progress("la-plume-t01", pageId = "page-020", fraction = 0.6),
      "le-phare-s01e01" to Progress("le-phare-s01e01", positionSeconds = 1300.0, fraction = 0.98),
      "le-phare-s01e02" to Progress("le-phare-s01e02", positionSeconds = 300.0, fraction = 0.23),
    )
}
