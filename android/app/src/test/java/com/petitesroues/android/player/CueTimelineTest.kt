package com.petitesroues.android.player

import com.petitesroues.android.line
import org.junit.Assert.assertEquals
import org.junit.Test

class CueTimelineTest {
  private val timeline = CueTimeline(listOf(line("a", 1.0, 3.0), line("b", 3.0, 5.0), line("c", 4.5, 7.0)))

  @Test
  fun currentIsTheLastStartedLine() {
    assertEquals(-1, timeline.current(0.5))
    assertEquals(0, timeline.current(2.0))
    assertEquals(1, timeline.current(2.999999))
    assertEquals(2, timeline.current(10.0))
  }

  @Test
  fun overlappingLinesShowTogether() {
    assertEquals(listOf("b", "c"), timeline.showing(4.6).map { it.line().id() })
    assertEquals(listOf<String>(), timeline.showing(8.0).map { it.line().id() })
  }

  @Test
  fun heldTimeStaysInsideAFinishedLineSoATouchingLineDoesNotShow() {
    val held = CueTimeline.heldTime(timeline.cues[0])
    assertEquals(listOf("a"), timeline.showing(held).map { it.line().id() })
  }

  @Test
  fun steppingBackRestartsTheLineThenMovesToThePreviousOne() {
    assertEquals(1.0, timeline.stepTarget(0.0, 1))
    assertEquals(3.0, timeline.stepTarget(2.0, 1))
    assertEquals(3.0, timeline.stepTarget(4.0, -1))
    assertEquals(1.0, timeline.stepTarget(3.2, -1))
    assertEquals(4.5, timeline.stepTarget(6.0, 1))
  }
}
