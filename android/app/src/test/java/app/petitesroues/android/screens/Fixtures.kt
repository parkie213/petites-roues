package app.petitesroues.android.screens

import android.net.Uri
import app.petitesroues.android.data.Progress
import app.petitesroues.android.library.Title
import app.petitesroues.android.library.TitleKind
import app.petitesroues.model.library.MarkdownPageEntry
import app.petitesroues.model.library.Repository
import app.petitesroues.model.pages.MarkdownPage

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
    media: Uri = Uri.parse("file:///fixtures/$path.media"),
    seriesPoster: Uri? = null,
  ) =
    Title(
      path = "fixtures/$path",
      name = name,
      series = series,
      kind = kind,
      documentUri = if (document) Uri.parse("file:///fixtures/$path.json") else null,
      mediaUri = media,
      documentId = path.substringAfterLast('/'),
      durationSeconds = if (kind == TitleKind.VIDEO) 1320.0 else null,
      season = season,
      episode = episode,
      volume = volume,
      poster = null,
      seriesPoster = seriesPoster,
      firstPage = null,
      sourceLanguage = "fr",
      mediaPath = "$path.media",
    )

  /** The example comic: the manga's cards show its first page. */
  val comic = Examples.media("comics/lulu/le-parapluie-de-lulu.cbz")

  /** The example series poster, on the TV series' card. */
  val poster = Examples.media("series/peppa-pig/poster.jpg")

  private fun episode(id: String, season: Int, episode: Int) =
    title(
      "series/le-phare-s0${season}e0$episode",
      "Le phare $season×$episode",
      TitleKind.VIDEO,
      "Le phare",
      season,
      episode,
      seriesPoster = poster,
    )

  val titles =
    listOf(
      title(
        "manga/la-plume-t01",
        "La Plume T01",
        TitleKind.COMIC,
        "La Plume",
        volume = 1,
        media = comic,
      ),
      title(
        "manga/la-plume-t02",
        "La Plume T02",
        TitleKind.COMIC,
        "La Plume",
        volume = 2,
        media = comic,
      ),
      episode("s01e01", 1, 1),
      episode("s01e02", 1, 2),
      episode("s02e01", 2, 1),
      title("films/la-riviere", "La rivière", TitleKind.VIDEO),
      title("films/holiday", "holiday", TitleKind.VIDEO, document = false),
    )

  val progress =
    mapOf(
      "la-plume-t01" to Progress("la-plume-t01", pageId = "page-020", fraction = 0.6),
      "le-phare-s01e01" to Progress("le-phare-s01e01", positionSeconds = 1300.0, fraction = 0.98),
      "le-phare-s01e02" to Progress("le-phare-s01e02", positionSeconds = 300.0, fraction = 0.23),
    )

  private fun page(folder: String, name: String, markdown: String) =
    MarkdownPageEntry(
      Repository("fixtures", "fixtures"),
      "$folder/$name.md",
      MarkdownPage(MarkdownPage.title(markdown, name), markdown),
    )

  /** The example note and two short made-up ones. */
  val notes =
    listOf(
      Examples.page("notes", "verb-plus-infinitive"),
      page(
        "notes",
        "il-y-a",
        "# Il y a\n\n`Il y a` is \"there is\" and \"there are\": *Il y a un chat. Il y a deux chats.*\n",
      ),
      page(
        "notes",
        "tu-et-vous",
        "# Tu and vous\n\n`Tu` for one friend, child or pet; `vous` for a stranger or for several people.\n",
      ),
    )

  /** The example quiz and a short made-up one. */
  val quizzes =
    listOf(
      Examples.page("quizzes", "verb-plus-infinitive"),
      page(
        "quizzes",
        "articles",
        "# Le, la or les\n\nGive a French noun and ask for its definite article. Explain each answer.\n",
      ),
    )
}
