package app.petitesroues.android.comic

/**
 * Groups page indices into the views the reader swipes between, ported from the browser's
 * `navigation.js`. In spread mode the cover stands alone and later pages pair as (1,2), (3,4)… in
 * manifest order; the reader decides their physical order from the reading direction.
 */
object Spreads {
  /** Pages shown together with page [index]. */
  fun pagesAt(index: Int, count: Int, spread: Boolean): List<Int> {
    if (!spread || index <= 0) return listOf(maxOf(0, index))
    val start = index - ((index - 1) % 2)
    return if (start + 1 < count) listOf(start, start + 1) else listOf(start)
  }

  /** Every view of a [count]-page comic in reading order. */
  fun views(count: Int, spread: Boolean): List<List<Int>> {
    val views = mutableListOf<List<Int>>()
    var index = 0
    while (index < count) {
      val view = pagesAt(index, count, spread)
      views += view
      index = view.last() + 1
    }
    return views
  }

  /** The view holding page [index]. */
  fun viewOf(views: List<List<Int>>, index: Int): Int =
    views.indexOfFirst { index in it }.coerceAtLeast(0)
}
