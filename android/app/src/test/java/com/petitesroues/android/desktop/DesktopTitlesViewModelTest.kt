package com.petitesroues.android.desktop

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petitesroues.android.Settings
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.FakeDesktop
import com.petitesroues.android.testing.FakeNsd
import com.petitesroues.android.testing.MainDispatcherRule
import com.petitesroues.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Syncing with the desktop, without drawing it: a [FakeDesktop] in place of the network, a real
 * [Transfers] scheduling its job with Robolectric's job scheduler, and a temporary download folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class DesktopTitlesViewModelTest {
  @get:Rule val main = MainDispatcherRule()
  @get:Rule val downloads = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val desktop =
    FakeDesktop(listOf(FakeDesktop.comic("Lulu", 1), FakeDesktop.comic("Lulu", 2)))
  private lateinit var settings: Settings
  private lateinit var transfers: Transfers

  @Before
  fun setUp() {
    settings =
      Settings(context).apply {
        this.desktop = FakeDesktop.DESKTOP
        downloadFolder = Uri.fromFile(downloads.root)
      }
    transfers =
      Transfers(context, settings, desktop, PairedDesktop(settings, DesktopFinder(FakeNsd())))
  }

  private fun viewModel() =
    DesktopTitlesViewModel(settings, transfers, context.contentResolver, main.dispatcher)

  private suspend fun DesktopTitlesViewModel.checked(): DesktopTitlesUiState = state.first {
    it.sync != null || it.problem != null
  }

  @Test
  fun `opening checks the desktop and lists what is not on the phone`() = runTest {
    val state = viewModel().checked()

    assertEquals("Bureau", state.desktop)
    val lulu = state.sync!!.groups().single()
    assertEquals("Lulu", lulu.name())
    assertFalse(lulu.followed())
    assertTrue(lulu.entries().all { it.state() == SyncStatus.State.MISSING })
    // Nothing is fetched until the series is followed or a title chosen.
    assertTrue(state.sync.toSync().isEmpty())
  }

  @Test
  fun `following a series syncs its missing titles`() = runTest {
    val model = viewModel()
    model.checked()

    model.follow("Lulu", true)

    assertEquals(setOf("Lulu"), settings.followed)
    val started = transfers.status.first { it.running }
    assertEquals("Waiting for Wi-Fi…", started.message)
    assertEquals(
      2,
      model.state.first { it.sync!!.groups().single().followed() }.sync!!.toSync().size,
    )
    assertEquals(2, transfers.pending.value)
  }

  @Test
  fun `choosing one title syncs just that one`() = runTest {
    val model = viewModel()
    model.checked()

    model.choose("Lulu-2", true)

    assertEquals(setOf("Lulu-2"), settings.chosen)
    transfers.status.first { it.running }
    val toSync = model.state.first { it.sync!!.toSync().isNotEmpty() }.sync!!.toSync()
    assertEquals(listOf("Lulu-2"), toSync.map { it.id() })
  }

  @Test
  fun `unfollowing does not start a sync`() = runTest {
    settings.followed = setOf("Lulu")
    val model = viewModel()
    model.checked()

    model.follow("Lulu", false)

    assertEquals(emptySet<String>(), settings.followed)
    assertTrue(
      model.state.first { !it.sync!!.groups().single().followed() }.sync!!.toSync().isEmpty()
    )
    assertFalse(transfers.status.value.running)
  }

  @Test
  fun `a desktop that refuses says so`() = runTest {
    desktop.refusing = "Not paired"

    val state = viewModel().checked()

    assertNull(state.sync)
    val problem = state.problem!!
    assertTrue(problem, problem.startsWith("Could not reach the desktop:"))
    assertTrue(problem, "Not paired" in problem)
  }

  @Test
  fun `stopping a sync says how to resume`() = runTest {
    val model = viewModel()
    model.checked()
    model.follow("Lulu", true)
    transfers.status.first { it.running }

    model.stop()

    assertFalse(transfers.status.value.running)
    assertEquals("Stopped. Download again to resume.", transfers.status.value.message)
  }
}
