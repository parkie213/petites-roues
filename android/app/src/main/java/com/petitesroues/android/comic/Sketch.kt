package com.petitesroues.android.comic

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/** The strokes drawn on the reader's current view, in content coordinates. Never saved. */
class Sketch {
  val strokes = mutableStateListOf<List<Offset>>()

  fun clear() = strokes.clear()
}

private val INK = Color(0xFFFF3B30)

/** Draws [sketch] over the content, in a constant on-screen width whatever the zoom [scale]. */
fun Modifier.sketch(sketch: Sketch, scale: () -> Float): Modifier = drawWithContent {
  drawContent()
  val width = 3.dp.toPx() / scale()
  for (points in sketch.strokes) {
    if (points.size == 1) {
      drawCircle(INK, width / 2, points[0])
      continue
    }
    val path =
      Path().apply {
        moveTo(points[0].x, points[0].y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
      }
    drawPath(path, INK, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

private enum class Mode {
  WAIT,
  IDLE,
  DRAW,
  PAN,
  ZOOM,
}

/**
 * Zoom, pan and drawing for the reader's current view, read in screen coordinates before the page
 * and the pager see the touch:
 * - a stylus, or a finger when [pencil] is on, draws once it moves past the touch slop;
 * - two fingers zoom about their centre and pan;
 * - one finger pans while the view is zoomed (the pager does not turn pages then).
 *
 * Anything else is left alone, so swipes turn pages and taps (a stylus tap too) reach the bubbles.
 * Gestures this handler takes over are consumed, which cancels the taps and page drags underneath.
 */
fun Modifier.zoomAndDraw(
  transform: () -> PageTransform,
  onTransform: (PageTransform) -> Unit,
  sketch: Sketch,
  pencil: () -> Boolean,
): Modifier =
  pointerInput(sketch) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
      val draws = stylus || pencil()
      var mode = Mode.WAIT
      var points = mutableListOf<Offset>()
      val width = size.width.toFloat()
      val height = size.height.toFloat()
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.none { it.pressed }) break
        if (!stylus && mode != Mode.ZOOM && event.changes.count { it.pressed } > 1) {
          if (mode == Mode.DRAW) sketch.strokes.removeAt(sketch.strokes.lastIndex)
          mode = Mode.ZOOM
        }
        val change = event.changes.firstOrNull { it.id == down.id }
        when (mode) {
          Mode.WAIT -> {
            if (change == null) mode = Mode.IDLE
            else if (
              (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            ) {
              mode =
                when {
                  draws -> Mode.DRAW
                  transform().zoomed -> Mode.PAN
                  else -> Mode.IDLE
                }
              if (mode == Mode.DRAW) {
                points =
                  mutableListOf(
                    transform().toContent(down.position),
                    transform().toContent(change.position),
                  )
                sketch.strokes.add(points.toList())
              }
              if (mode != Mode.IDLE) event.changes.forEach { it.consume() }
            }
          }
          Mode.DRAW -> {
            if (change != null) {
              points += transform().toContent(change.position)
              sketch.strokes[sketch.strokes.lastIndex] = points.toList()
            }
            event.changes.forEach { it.consume() }
          }
          Mode.PAN -> {
            if (change != null)
              onTransform(
                transform().transformed(1f, Offset.Zero, change.positionChange(), width, height)
              )
            event.changes.forEach { it.consume() }
          }
          Mode.ZOOM -> {
            onTransform(
              transform()
                .transformed(
                  event.calculateZoom(),
                  event.calculateCentroid(useCurrent = true),
                  event.calculatePan(),
                  width,
                  height,
                )
            )
            event.changes.forEach { it.consume() }
          }
          Mode.IDLE -> Unit
        }
      }
    }
  }
