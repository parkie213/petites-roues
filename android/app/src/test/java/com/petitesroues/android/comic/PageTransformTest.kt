package com.petitesroues.android.comic

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTransformTest {
  private val none = PageTransform()

  @Test
  fun zoomKeepsThePinchPointStill() {
    val zoomed = none.transformed(2f, Offset(300f, 400f), Offset.Zero, 1000f, 2000f)
    assertEquals(2f, zoomed.scale)
    assertEquals(Offset(-300f, -400f), zoomed.offset)
    assertEquals(Offset(300f, 400f), zoomed.toContent(Offset(300f, 400f)))
    assertTrue(zoomed.zoomed)
  }

  @Test
  fun scaleStaysBetweenOneAndFour() {
    assertEquals(4f, none.transformed(10f, Offset.Zero, Offset.Zero, 1000f, 2000f).scale)
    val back =
      none
        .transformed(2f, Offset(500f, 500f), Offset.Zero, 1000f, 2000f)
        .transformed(0.1f, Offset(500f, 500f), Offset.Zero, 1000f, 2000f)
    assertEquals(PageTransform(), back)
    assertFalse(back.zoomed)
  }

  @Test
  fun panStopsAtThePageEdges() {
    val zoomed = none.transformed(2f, Offset.Zero, Offset.Zero, 1000f, 2000f)
    assertEquals(
      Offset.Zero,
      zoomed.transformed(1f, Offset.Zero, Offset(500f, 500f), 1000f, 2000f).offset,
    )
    assertEquals(
      Offset(-1000f, -2000f),
      zoomed.transformed(1f, Offset.Zero, Offset(-5000f, -5000f), 1000f, 2000f).offset,
    )
    assertEquals(
      Offset.Zero,
      none.transformed(1f, Offset.Zero, Offset(-50f, 30f), 1000f, 2000f).offset,
    )
  }

  @Test
  fun screenPointsMapBackToContent() {
    val t = PageTransform(2f, Offset(-100f, -200f))
    assertEquals(Offset(150f, 300f), t.toContent(Offset(200f, 400f)))
  }
}
