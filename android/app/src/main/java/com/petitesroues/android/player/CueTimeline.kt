package com.petitesroues.android.player

import com.petitesroues.model.document.VideoCue

/**
 * Time lookups over a document's chronologically sorted cues, ported from the browser player's
 * `model.js` and `playback.js` so both players show and step through lines identically.
 */
class CueTimeline(val cues: List<VideoCue>) {
  /** The last cue that has started at [time], or -1 before the first. */
  fun current(time: Double): Int {
    // Seeks can land a hair before the requested time; don't flash the previous line.
    for (i in cues.indices.reversed()) if (time >= cues[i].start() - EPSILON) return i
    return -1
  }

  /** Every cue on screen at [time]; overlapping lines are shown together. */
  fun showing(time: Double): List<VideoCue> = cues.filter {
    time >= it.start() - EPSILON && time < it.end()
  }

  /**
   * Where a previous/next line step lands. Stepping back shortly after a line starts goes to the
   * line before, so repeated steps keep moving even though playback advanced since the last one.
   */
  fun stepTarget(time: Double, direction: Int): Double? {
    if (cues.isEmpty()) return null
    val index = if (time < cues[0].start()) -1 else current(time)
    if (index < 0) return cues[0].start()
    val target =
      if (direction > 0) minOf(index + 1, cues.size - 1)
      else if (time > cues[index].start() + RESTART_WINDOW) index else maxOf(0, index - 1)
    return cues[target].start()
  }

  companion object {
    private const val EPSILON = 0.00001
    private const val RESTART_WINDOW = 0.5

    /**
     * Where the study cursor rests after auto-pausing at a line's end: just inside the finished
     * line, so a touching next line does not appear until playback resumes.
     */
    fun heldTime(cue: VideoCue): Double = maxOf(cue.start(), cue.end() - 0.001)
  }
}
