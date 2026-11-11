package app.petitesroues.android.comic

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsTest {
  @Test
  fun coverStandsAloneThenPagesPair() {
    assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), Spreads.views(6, true))
    assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), Spreads.views(5, true))
  }

  @Test
  fun singlePagesWithoutSpreads() {
    assertEquals(listOf(listOf(0), listOf(1), listOf(2)), Spreads.views(3, false))
  }

  @Test
  fun viewOfFindsThePageAfterRotation() {
    val views = Spreads.views(6, true)
    assertEquals(2, Spreads.viewOf(views, 4))
    assertEquals(0, Spreads.viewOf(views, 0))
  }
}
