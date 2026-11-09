package com.petitesroues.android.screens

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.petitesroues.android.AppTheme
import com.petitesroues.android.Settings
import com.petitesroues.android.comic.ReadScreen
import com.petitesroues.android.data.Progress
import com.petitesroues.android.library.TitleKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The comic reader on the example comic, from the start and where a reader left off. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [SDK], qualifiers = PHONE)
class ReaderScreenshotTest {
  @get:Rule val compose = createComposeRule()

  private fun reader(saved: Progress? = null) {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val document = Examples.document(LULU)
    val title = Examples.title(LULU, "$LULU.cbz", TitleKind.COMIC, document)
    compose.setContent {
      AppTheme {
        ReadScreen(
          title,
          document,
          saved,
          Examples.progress(context),
          Settings(context),
          onBack = {},
        )
      }
    }
    // Pages decode on a background thread behind a spinner; capture once it has gone.
    compose.waitUntil(timeoutMillis = 10_000) {
      compose
        .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .fetchSemanticsNodes()
        .isEmpty()
    }
    compose.onRoot().captureRoboImage()
  }

  @Test fun firstPage() = reader()

  @Test fun savedPage() = reader(Progress(Examples.document(LULU).id(), pageId = "page-1"))

  private companion object {
    const val LULU = "comics/lulu/le-parapluie-de-lulu"
  }
}
