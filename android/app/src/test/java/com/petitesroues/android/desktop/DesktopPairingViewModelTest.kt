package com.petitesroues.android.desktop

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petitesroues.android.Settings
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.FakeDesktop
import com.petitesroues.android.testing.FakeNsd
import com.petitesroues.android.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pairing with the desktop, testing and forgetting it, against a [FakeDesktop]. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class DesktopPairingViewModelTest {
  @get:Rule val main = MainDispatcherRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val desktop = FakeDesktop(listOf(FakeDesktop.comic("Lulu", 1)))
  private lateinit var settings: Settings

  @Before
  fun setUp() {
    settings = Settings(context)
  }

  private fun viewModel() =
    DesktopPairingViewModel(
      context,
      settings,
      desktop,
      PairedDesktop(settings, DesktopFinder(FakeNsd())),
      main.dispatcher,
    )

  private suspend fun DesktopPairingViewModel.done() = state.first { !it.busy }

  @Test
  fun `pairing saves the desktop and tests the connection`() = runTest {
    val model = viewModel()

    model.pair(FakeDesktop.PAIRING_CODE)

    val state = model.done()
    assertEquals(FakeDesktop.DESKTOP.url(), settings.desktop?.url())
    assertEquals("Bureau", state.desktop?.name())
    assertEquals("Connected to Bureau: 1 study documents, 1 media files.", state.status)
  }

  @Test
  fun `a QR code from elsewhere is not paired with`() = runTest {
    val model = viewModel()

    model.pair("https://example.com")

    val state = model.done()
    assertNull(settings.desktop)
    assertNull(state.desktop)
    assertEquals("Could not pair: This QR code is not from petites roues", state.status)
  }

  @Test
  fun `a desktop that refuses the test says why`() = runTest {
    settings.desktop = FakeDesktop.DESKTOP
    desktop.refusing = "This phone was forgotten"
    val model = viewModel()

    model.test()

    val status = model.done().status!!
    assertEquals(true, status.startsWith("Could not connect:"))
    assertEquals(true, "This phone was forgotten" in status)
  }

  @Test
  fun `forgetting unpairs`() = runTest {
    settings.desktop = FakeDesktop.DESKTOP
    val model = viewModel()
    model.test()
    model.done()

    model.forget()

    assertNull(settings.desktop)
    assertEquals(DesktopPairingUiState(null), model.state.value)
  }

  @Test
  fun `reopening the dialog drops the last status`() = runTest {
    settings.desktop = FakeDesktop.DESKTOP
    val model = viewModel()
    model.report("Could not scan")

    model.onShown()

    assertEquals(DesktopPairingUiState(FakeDesktop.DESKTOP), model.state.value)
  }
}
