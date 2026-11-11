package app.petitesroues.android.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.android.AppTheme
import app.petitesroues.android.Settings
import app.petitesroues.android.library.TitleKind
import app.petitesroues.android.player.PlayerViewModel
import app.petitesroues.android.player.WatchScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The video player on an example episode. Robolectric doesn't decode video, so the controls and
 * subtitles are drawn over black.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [SDK], qualifiers = PHONE_LANDSCAPE)
class PlayerScreenshotTest {
  @get:Rule val compose = createComposeRule()

  private fun player(controlsOnLeft: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val settings = Settings(context).apply { this.controlsOnLeft = controlsOnLeft }
    val document = Examples.document(PEPPA)
    val title =
      Examples.title(
        PEPPA,
        "series/peppa-pig/s01e01-les-flaques-de-boue.mp4",
        TitleKind.VIDEO,
        document,
      )
    // The player redraws every frame while it is open, so it never idles: step the clock by hand.
    compose.mainClock.autoAdvance = false
    val model =
      PlayerViewModel(title, document, null, context, settings, Examples.progress(context))
    compose.setContent { AppTheme { WatchScreen(model, onBack = {}) } }
    // ExoPlayer reads the file's length on its own thread; capture once the player shows it.
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.mainClock.advanceTimeBy(100)
      compose.onAllNodesWithText("1:02", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onRoot().captureRoboImage()
  }

  @Test fun controls() = player(controlsOnLeft = false)

  /** The rail moved to the left with the top bar's swap button. */
  @Test fun controlsOnLeft() = player(controlsOnLeft = true)

  private companion object {
    const val PEPPA = "series/peppa-pig/s01e01"
  }
}

/** The same phone turned on its side, as the player is watched. */
const val PHONE_LANDSCAPE = "w891dp-h411dp-land-xxhdpi"
