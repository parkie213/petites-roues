package com.petitesroues.android.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors the web library's `shelves.test.mjs`, so both libraries group titles alike. */
class ShelvesTest {
  private data class Entry(
    val id: String,
    override val name: String = "Untitled",
    override val series: String? = null,
    override val kind: TitleKind = TitleKind.VIDEO,
    override val season: Int? = null,
    override val episode: Int? = null,
    override val volume: Int? = null,
    override val documentId: String = id,
    override val mediaAvailable: Boolean = true,
    val poster: Boolean = false,
    override val hasSeriesPoster: Boolean = false,
    val folder: String = "one",
  ) : Shelvable {
    override val hasTitleArt: Boolean
      get() = poster || (kind == TitleKind.COMIC && mediaAvailable)
  }

  private var next = 0

  private fun id() = "title-${++next}"

  private fun tome(volume: Int, id: String = id(), seriesPoster: Boolean = false) =
    Entry(
      id,
      name = "Demon Slayer — Tome $volume",
      series = "Demon Slayer",
      kind = TitleKind.COMIC,
      volume = volume,
      hasSeriesPoster = seriesPoster,
    )

  private fun episode(
    season: Int,
    number: Int,
    series: String = "Peppa Pig",
    folder: String = "one",
    media: Boolean = true,
  ) = Entry(id(), name = "Episode $number", series = series, season = season, episode = number, folder = folder, mediaAvailable = media)

  private fun shelf(result: List<Shelf<Entry>>, key: String) = result.single { it.key == key }.groups

  @Test
  fun mangaAndAnimeOfOneSeriesAreSeparateCardsAndASeriesFilmIsAFilm() {
    val result =
      Shelves.shelves(
        listOf(
          tome(2),
          tome(1),
          tome(10),
          episode(1, 1, series = "Demon Slayer"),
          Entry(id(), name = "Mugen Train", series = "Demon Slayer"),
          Entry(id(), name = "Princesse Mononoké"),
        )
      )
    val manga = shelf(result, "manga").single()
    assertEquals("Demon Slayer", manga.name)
    assertEquals("3 tomes", manga.count)
    assertEquals(listOf("Tome 1", "Tome 2", "Tome 10"), manga.slots.map { it.label })
    val anime = shelf(result, "series").single()
    assertEquals("Demon Slayer", anime.name)
    assertEquals("1 episode", anime.count)
    assertEquals(
      listOf("Mugen Train" to true, "Princesse Mononoké" to true),
      shelf(result, "films").map { it.name to it.single },
    )
  }

  @Test
  fun episodesSortBySeasonThenEpisodeAndSeasonsAreCountedOnlyWhenSeveral() {
    var peppa = shelf(Shelves.shelves(listOf(episode(1, 2), episode(1, 10), episode(1, 1))), "series").single()
    assertEquals("3 episodes", peppa.count)
    assertEquals(listOf("S1 · E1", "S1 · E2", "S1 · E10"), peppa.slots.map { it.detail })

    peppa = shelf(Shelves.shelves(listOf(episode(2, 1), episode(1, 1), episode(1, 2))), "series").single()
    assertEquals(listOf(1, 2), peppa.seasons)
    assertEquals("2 seasons · 3 episodes", peppa.count)
    assertEquals(listOf("S1 · E1", "S1 · E2", "S2 · E1"), peppa.slots.map { it.detail })
  }

  @Test
  fun aSeriesWithoutNumbersSuchAsAChannelStaysASeriesOfVideos() {
    val parisien =
      shelf(
          Shelves.shelves(
            listOf(
              Entry(id(), name = "Paris : cette boulangerie", series = "Le Parisien"),
              Entry(id(), name = "Des frigos piratés", series = "Le Parisien"),
            )
          ),
          "series",
        )
        .single()
    assertEquals("2 videos", parisien.count)
    assertEquals(listOf("Des frigos piratés", "Paris : cette boulangerie"), parisien.slots.map { it.label })
  }

  @Test
  fun twoFoldersMergeIntoOneSeriesAndTheSameEpisodeBecomesOneSlotWithCopies() {
    val groups =
      shelf(
        Shelves.shelves(
          listOf(
            episode(1, 1, folder = "one", media = false),
            episode(1, 2, folder = "one", series = "Peppa  pig"),
            episode(1, 1, folder = "two"),
            episode(2, 1, folder = "two", series = "peppa pig "),
          )
        ),
        "series",
      )
    val peppa = groups.single()
    assertEquals("the most common spelling is shown", "Peppa Pig", peppa.name)
    assertEquals(3, peppa.slots.size)
    val first = peppa.slots.first()
    assertEquals(2, first.copies.size)
    assertEquals("the copy with media is preferred", "two", first.entry.folder)
  }

  @Test
  fun equallyAvailableCopiesKeepFolderOrder() {
    val film =
      shelf(
          Shelves.shelves(
            listOf(
              Entry("a", documentId = "robots", name = "ROBOTS", folder = "one"),
              Entry("b", documentId = "robots", name = "ROBOTS", folder = "two"),
            )
          ),
          "films",
        )
        .single()
    assertEquals(2, film.slots.single().copies.size)
    assertEquals("a", film.slots.single().entry.id)
  }

  @Test
  fun cardArtIsTheSeriesPosterElseTheFirstSlotWithArtElseNone() {
    var manga = shelf(Shelves.shelves(listOf(tome(2), tome(1, id = "t1"))), "manga").single()
    assertEquals("t1", manga.art?.copy?.id)
    assertEquals(false, manga.art?.series)
    assertEquals("t1", manga.slots.first().art?.copy?.id)

    manga = shelf(Shelves.shelves(listOf(tome(1), tome(2, id = "t2", seriesPoster = true))), "manga").single()
    assertEquals(CardArt(manga.slots[1].entry, series = true), manga.art)

    val peppa = shelf(Shelves.shelves(listOf(episode(1, 1), episode(1, 2))), "series").single()
    assertNull("videos have no art without a poster", peppa.art)
    val film = shelf(Shelves.shelves(listOf(Entry("f", name = "Film", poster = true))), "films").single()
    assertEquals("f", film.art?.copy?.id)
  }
}
