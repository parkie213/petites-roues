package app.petitesroues.android.desktop

import app.petitesroues.android.screens.SDK
import app.petitesroues.android.testing.FakeNsd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [DesktopFinder.find] against a [FakeNsd] that, like Android, can call back after a search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class DesktopFinderSearchTest {
  private val pin = "sha256:" + "ab".repeat(32)
  private val nsd = FakeNsd(listOf(FakeNsd.announcement("desktop", pin, "192.168.1.42")))

  @Test
  fun aLateConfirmationAfterTheSearchIsHarmless() {
    assertEquals("https://192.168.1.42:8878", DesktopFinder(nsd).find(pin))
    // Android confirms the unregistration after find has returned; this must not throw.
    nsd.confirmUnregistrations()
  }

  @Test
  fun aDesktopFoundAfterTheSearchIsNotLookedUp() {
    assertEquals("https://192.168.1.42:8878", DesktopFinder(nsd).find(pin))
    nsd.discovery.onServiceFound(FakeNsd.service("late"))
    assertTrue("lookups left registered: ${nsd.registered.size}", nsd.registered.isEmpty())
  }

  @Test
  fun aSearchThatCannotStartFindsNothing() {
    assertNull(DesktopFinder(FakeNsd()).find(pin))
  }
}
