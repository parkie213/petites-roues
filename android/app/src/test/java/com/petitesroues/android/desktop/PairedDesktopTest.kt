package com.petitesroues.android.desktop

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petitesroues.android.Settings
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.FakeDesktop
import com.petitesroues.android.testing.FakeNsd
import com.petitesroues.model.sync.Desktop
import com.petitesroues.sync.DesktopException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Reaching the paired desktop ([FakeDesktop.DESKTOP], saved at 192.168.1.5) where it is now, with a
 * [FakeNsd] in place of the local network.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class PairedDesktopTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val moved = "https://192.168.1.42:8878"
  private lateinit var settings: Settings

  @Before
  fun setUp() {
    settings = Settings(context).apply { desktop = FakeDesktop.DESKTOP }
  }

  /** The paired desktop, looked for through [nsd]. */
  private fun paired(nsd: FakeNsd) = PairedDesktop(settings, DesktopFinder(nsd))

  /** A network where the desktop announces itself at 192.168.1.42. */
  private val announced =
    FakeNsd(listOf(FakeNsd.announcement("Bureau", FakeDesktop.PIN, "192.168.1.42")))

  /** A call that only the desktop at [url] answers, saying which address it reached. */
  private fun answeredAt(url: String): (Desktop) -> String = { desktop ->
    if (desktop.url() != url) throw IOException("${desktop.url()} did not answer")
    desktop.url()
  }

  @Test
  fun `a desktop at its saved address is reached without a search`() {
    val nsd = announced
    assertEquals(
      FakeDesktop.DESKTOP.url(),
      paired(nsd).reach(answeredAt(FakeDesktop.DESKTOP.url())),
    )
    assertEquals(0, nsd.searches)
  }

  @Test
  fun `a desktop that moved is reached and its new address saved`() {
    assertEquals(moved, paired(announced).reach(answeredAt(moved)))
    assertEquals(moved, settings.desktop?.url())
    assertEquals(FakeDesktop.DESKTOP.token(), settings.desktop?.token())
  }

  @Test
  fun `a new address that does not answer either is not saved`() {
    assertThrows(IOException::class.java) { paired(announced).reach(answeredAt("nowhere")) }
    assertEquals(FakeDesktop.DESKTOP.url(), settings.desktop?.url())
  }

  @Test
  fun `a refusal is not followed by a search`() {
    val nsd = announced
    val refused = DesktopException(403, "Not paired")
    val thrown = assertThrows(DesktopException::class.java) { paired(nsd).reach { throw refused } }
    assertSame(refused, thrown)
    assertEquals(0, nsd.searches)
  }

  @Test
  fun `a desktop found nowhere else gives the original failure`() {
    val unreachable = IOException("no answer")
    val thrown =
      assertThrows(IOException::class.java) { paired(FakeNsd()).reach { throw unreachable } }
    assertSame(unreachable, thrown)
    assertEquals(FakeDesktop.DESKTOP.url(), settings.desktop?.url())
  }

  @Test
  fun `an unpaired phone cannot reach a desktop`() {
    settings.desktop = null
    assertThrows(IllegalStateException::class.java) { paired(announced).reach {} }
  }
}
