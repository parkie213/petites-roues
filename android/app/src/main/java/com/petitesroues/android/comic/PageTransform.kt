package com.petitesroues.android.comic

import androidx.compose.ui.geometry.Offset

/**
 * How the reader's current view is zoomed and panned: content point p is shown at `p * scale +
 * offset`, with the transform origin at the view's top-left corner. [scale] stays within
 * [MIN]..[MAX], and the offset keeps the zoomed view covering its slot, so a pan never shows past a
 * page's edge.
 */
data class PageTransform(val scale: Float = MIN, val offset: Offset = Offset.Zero) {
  val zoomed: Boolean
    get() = scale > MIN

  /**
   * Zoomed by [zoom] about the screen point [centroid] and moved by [pan], then clamped to a view
   * of [width] × [height].
   */
  fun transformed(
    zoom: Float,
    centroid: Offset,
    pan: Offset,
    width: Float,
    height: Float,
  ): PageTransform {
    val next = (scale * zoom).coerceIn(MIN, MAX)
    val moved = centroid - (centroid - offset) * (next / scale) + pan
    return PageTransform(next, clamp(moved, next, width, height))
  }

  /** The content point under the screen point [screen]. */
  fun toContent(screen: Offset): Offset = (screen - offset) / scale

  companion object {
    const val MIN = 1f
    const val MAX = 4f

    private fun clamp(offset: Offset, scale: Float, width: Float, height: Float) =
      Offset(
        offset.x.coerceIn(width * (1 - scale), 0f),
        offset.y.coerceIn(height * (1 - scale), 0f),
      )
  }
}
