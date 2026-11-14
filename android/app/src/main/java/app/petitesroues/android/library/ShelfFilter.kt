package app.petitesroues.android.library

import app.petitesroues.library.shelves.Shelves.Shelf

/**
 * The shelf the library's chips narrow to: [chosen] when a shelf with that key is in [shelves],
 * otherwise null, which means All. A shelf can vanish after a Refresh, so a remembered choice is
 * never trusted without this check.
 */
fun <T> shelfChoice(shelves: List<Shelf<T>>, chosen: String?): String? = chosen?.takeIf { key ->
  shelves.any { it.key == key }
}

/**
 * The shelves to show for the chip [chosen]: only that shelf, or every shelf, in order and with
 * unchanged groups, when nothing (All) or an unknown shelf is chosen. It never regroups cards;
 * grouping stays with [app.petitesroues.library.shelves.Shelves].
 */
fun <T> narrowShelves(shelves: List<Shelf<T>>, chosen: String?): List<Shelf<T>> =
  shelfChoice(shelves, chosen)?.let { key -> shelves.filter { it.key == key } } ?: shelves
