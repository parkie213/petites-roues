package app.petitesroues.android.library

import app.petitesroues.library.shelves.Shelves.Group
import app.petitesroues.library.shelves.Shelves.Shelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfFilterTest {
  private val comics = Shelf<String>("comics", "Comics", emptyList<Group<String>>(), false)
  private val series = Shelf<String>("series", "Series", emptyList<Group<String>>(), false)
  private val shelves = listOf(comics, series)

  @Test
  fun allKeepsEveryShelfInOrder() {
    assertEquals(shelves, narrowShelves(shelves, null))
  }

  @Test
  fun aChosenShelfIsTheOnlyOneShown() {
    assertEquals(listOf(series), narrowShelves(shelves, "series"))
    assertEquals("series", shelfChoice(shelves, "series"))
  }

  @Test
  fun aShelfThatVanishedFallsBackToAll() {
    assertEquals(shelves, narrowShelves(shelves, "films"))
    assertNull(shelfChoice(shelves, "films"))
    assertNull(shelfChoice(emptyList<Shelf<String>>(), "series"))
  }

  @Test
  fun aLandscapeCardSpansTwoColumnsOrTheWholeRowWhenThereIsOne() {
    assertEquals(1, landscapeSpan(false, 3).currentLineSpan)
    assertEquals(2, landscapeSpan(true, 3).currentLineSpan)
    assertEquals(1, landscapeSpan(true, 1).currentLineSpan)
  }
}
