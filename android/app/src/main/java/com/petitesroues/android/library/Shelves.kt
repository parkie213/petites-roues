package com.petitesroues.android.library

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** What [Shelves] needs from a title, so the grouping runs without Android types. */
interface Shelvable {
  val kind: TitleKind
  val name: String
  val series: String?
  val season: Int?
  val episode: Int?
  val volume: Int?
  val documentId: String
  /** Whether the title's video or CBZ is present, so it can open. */
  val mediaAvailable: Boolean
  /** Whether the title has its own card image: a poster, else a comic's first page. */
  val hasTitleArt: Boolean
  val hasSeriesPoster: Boolean
}

/** A card image: [copy]'s own art, or the poster of the series folder above it. */
data class CardArt<T>(val copy: T, val series: Boolean)

/**
 * One tome, episode or film. Copies of it from several content folders or editions share the
 * slot; [entry] is the preferred copy, one whose media is present.
 */
data class Slot<T>(
  val key: String,
  val entry: T,
  val copies: List<T>,
  val label: String,
  val detail: String?,
  val art: CardArt<T>?,
)

/** A card on a shelf: a whole series, or a [single] title. */
data class ShelfGroup<T>(
  val key: String,
  val shelf: String,
  val name: String,
  val single: Boolean,
  val slots: List<Slot<T>>,
  val seasons: List<Int>,
  val count: String?,
  val art: CardArt<T>?,
)

data class Shelf<T>(val key: String, val label: String, val groups: List<ShelfGroup<T>>)

/**
 * Groups titles into Manga, Series and Films shelves from document metadata only; folder names
 * never appear. Ported from the web library's `shelves.js` so both libraries group alike.
 *
 * - Manga: comics, grouped by series; a series' slots are its volumes.
 * - Series: videos with a season or episode, grouped by series. A series whose videos have no
 *   numbers at all (a channel) is a series too, ordered by title.
 * - Films: other videos, one card each, even when they name a series that has episodes.
 *
 * Titles arrive in content-folder order, which breaks ties between equally available copies.
 */
object Shelves {
  /** Series names match ignoring case, spacing and Unicode composition. */
  fun seriesKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

  /** The three shelves, each with its cards sorted by name; empty shelves are kept. */
  fun <T : Shelvable> shelves(entries: List<T>): List<Shelf<T>> {
    val comics = entries.filter { it.kind == TitleKind.COMIC }
    val videos = entries.filter { it.kind != TitleKind.COMIC }

    val manga = mutableListOf<ShelfGroup<T>>()
    for ((key, group) in bySeries(comics.filter(::hasSeries))) manga += seriesGroup("manga", key, group)
    manga += singleGroups("manga", comics.filterNot(::hasSeries))

    val series = mutableListOf<ShelfGroup<T>>()
    val loose = videos.filterNot(::hasSeries).toMutableList()
    for ((key, group) in bySeries(videos.filter(::hasSeries))) {
      if (group.none(::numbered)) {
        series += seriesGroup("series", key, group)
        continue
      }
      series += seriesGroup("series", key, group.filter(::numbered))
      loose += group.filterNot(::numbered)
    }
    val films = singleGroups("films", loose)

    val byName = Comparator<ShelfGroup<T>> { a, b -> natural.compare(a.name, b.name) }
    return listOf(
      Shelf("manga", "Manga", manga.sortedWith(byName)),
      Shelf("series", "Series", series.sortedWith(byName)),
      Shelf("films", "Films", films.sortedWith(byName)),
    )
  }

  private fun hasSeries(entry: Shelvable) = !entry.series.isNullOrBlank()

  private fun numbered(entry: Shelvable) = entry.season != null || entry.episode != null

  private fun <T : Shelvable> bySeries(entries: List<T>): Map<String, List<T>> =
    entries.groupBy { seriesKey(it.series!!) }

  private fun slotKey(entry: Shelvable): String =
    when {
      entry.kind == TitleKind.COMIC && entry.volume != null -> "v${entry.volume}"
      entry.kind != TitleKind.COMIC && numbered(entry) -> "s${entry.season ?: ""}e${entry.episode ?: ""}"
      else -> "d:${entry.documentId}"
    }

  private fun slotLabel(entry: Shelvable): String =
    if (entry.kind == TitleKind.COMIC && entry.volume != null) "Tome ${entry.volume}" else entry.name

  private fun slotDetail(entry: Shelvable): String? {
    if (entry.kind == TitleKind.COMIC || !numbered(entry)) return null
    return listOfNotNull(entry.season?.let { "S$it" }, entry.episode?.let { "E$it" }).joinToString(" · ")
  }

  private val slotOrder =
    compareBy<Slot<out Shelvable>>(
        { it.entry.volume ?: Int.MAX_VALUE },
        { it.entry.season ?: 0 },
        { it.entry.episode ?: Int.MAX_VALUE },
      )
      .thenComparator { a, b -> natural.compare(a.entry.name, b.entry.name) }

  private fun <T : Shelvable> makeSlots(entries: List<T>): List<Slot<T>> =
    entries
      .groupBy(::slotKey)
      .map { (key, copies) ->
        // Stable sort: equally available copies keep content-folder order.
        val ordered = copies.sortedByDescending { it.mediaAvailable }
        val entry = ordered.first()
        Slot(
          key,
          entry,
          ordered,
          slotLabel(entry),
          slotDetail(entry),
          ordered.firstOrNull { it.hasTitleArt }?.let { CardArt(it, series = false) },
        )
      }
      .sortedWith(slotOrder)

  private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

  private fun <T : Shelvable> seriesGroup(shelf: String, key: String, entries: List<T>): ShelfGroup<T> {
    val slots = makeSlots(entries)
    val seasons = slots.mapNotNull { it.entry.season }.distinct().sorted()
    val unit = if (shelf == "manga") "tome" else if (entries.any(::numbered)) "episode" else "video"
    val names = entries.map { it.series!!.trim() }
    // The most common spelling; ties go to the first seen.
    val name = names.groupingBy { it }.eachCount().maxBy { it.value }.key
    val poster = slots.flatMap { it.copies }.firstOrNull { it.hasSeriesPoster }
    return ShelfGroup(
      key = "$shelf:$key",
      shelf = shelf,
      name = name,
      single = false,
      slots = slots,
      seasons = seasons,
      count =
        if (seasons.size > 1) "${plural(seasons.size, "season")} · ${plural(slots.size, unit)}"
        else plural(slots.size, unit),
      art = poster?.let { CardArt(it, series = true) } ?: slots.firstNotNullOfOrNull { it.art },
    )
  }

  private fun <T : Shelvable> singleGroups(shelf: String, entries: List<T>): List<ShelfGroup<T>> =
    makeSlots(entries).map { slot ->
      ShelfGroup("$shelf:${slot.key}", shelf, slot.entry.name, true, listOf(slot), emptyList(), null, slot.art)
    }

  /** Locale-aware order that compares digit runs as numbers, so "Tome 2" precedes "Tome 10". */
  val natural: Comparator<String> =
    object : Comparator<String> {
      private val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
      private val parts = Regex("\\d+|\\D+")

      override fun compare(a: String, b: String): Int {
        val x = parts.findAll(a).map { it.value }.toList()
        val y = parts.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
          val p = x[i]
          val q = y[i]
          val order =
            if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger())
            else collator.compare(p, q)
          if (order != 0) return order
        }
        return x.size.compareTo(y.size).takeIf { it != 0 } ?: collator.compare(a, b)
      }
    }
}
