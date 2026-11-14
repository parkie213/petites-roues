package app.petitesroues.android.library

/**
 * Where in a video a landscape card's frame is grabbed, in microseconds: a quarter of the way
 * through, past opening credits and intros. Without a known duration, the start.
 */
fun framePositionMicros(durationMillis: Long?): Long =
  if (durationMillis == null || durationMillis <= 0) 0L else durationMillis * 1000 / 4

/** A rectangle of a frame, in pixels. */
data class CropBox(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The largest centered 16:9 rectangle of a [width] by [height] frame, so a 4:3 or portrait video
 * loses its edges rather than being stretched.
 */
fun landscapeCrop(width: Int, height: Int): CropBox {
  val wider = width * 9 > height * 16
  val cropWidth = if (wider) height * 16 / 9 else width
  val cropHeight = if (wider) height else width * 9 / 16
  return CropBox((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
}
