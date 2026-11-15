package app.petitesroues.android.library

import org.junit.Assert.assertEquals
import org.junit.Test

class CardFramesTest {
  @Test
  fun theFrameIsAQuarterOfTheWayThroughTheVideo() {
    assertEquals(15_000_000L, framePositionMicros(60_000))
    assertEquals(250_000L, framePositionMicros(1_000))
  }

  @Test
  fun anUnknownDurationGrabsTheFirstFrame() {
    assertEquals(0L, framePositionMicros(null))
    assertEquals(0L, framePositionMicros(0))
    assertEquals(0L, framePositionMicros(-5))
  }

  @Test
  fun aSixteenByNineFrameIsKeptWhole() {
    assertEquals(CropBox(0, 0, 1920, 1080), landscapeCrop(1920, 1080))
  }

  @Test
  fun aFourByThreeFrameLosesItsTopAndBottom() {
    assertEquals(CropBox(0, 30, 320, 180), landscapeCrop(320, 240))
  }

  @Test
  fun aPortraitFrameLosesItsTopAndBottomToo() {
    assertEquals(CropBox(0, 656, 1080, 607), landscapeCrop(1080, 1920))
  }

  @Test
  fun anUltrawideFrameLosesItsSides() {
    assertEquals(CropBox(569, 0, 1422, 800), landscapeCrop(2560, 800))
  }
}
