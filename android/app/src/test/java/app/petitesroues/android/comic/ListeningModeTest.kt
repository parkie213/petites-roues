package app.petitesroues.android.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningModeTest {
  @Test
  fun cyclesThroughAllModesInOrder() {
    assertEquals(ListeningMode.LISTEN_FIRST, ListeningMode.OFF.next())
    assertEquals(ListeningMode.LISTEN_AND_OPEN, ListeningMode.LISTEN_FIRST.next())
    assertEquals(ListeningMode.OFF, ListeningMode.LISTEN_AND_OPEN.next())
  }

  @Test
  fun offOpensWithoutSpeakingOnEveryTap() {
    for (heard in listOf(false, true)) {
      assertFalse(ListeningMode.OFF.readsAloud(heard))
      assertTrue(ListeningMode.OFF.opensCard(heard))
    }
  }

  @Test
  fun listenFirstReadsThenOpensWithoutRepeatingSpeech() {
    assertTrue(ListeningMode.LISTEN_FIRST.readsAloud(false))
    assertFalse(ListeningMode.LISTEN_FIRST.opensCard(false))
    assertFalse(ListeningMode.LISTEN_FIRST.readsAloud(true))
    assertTrue(ListeningMode.LISTEN_FIRST.opensCard(true))
  }

  @Test
  fun combinedModeReadsAndOpensIncludingRepeatedTaps() {
    for (heard in listOf(false, true)) {
      assertTrue(ListeningMode.LISTEN_AND_OPEN.readsAloud(heard))
      assertTrue(ListeningMode.LISTEN_AND_OPEN.opensCard(heard))
    }
  }
}
