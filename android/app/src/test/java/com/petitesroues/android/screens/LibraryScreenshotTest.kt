package com.petitesroues.android.screens

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.petitesroues.android.AppTheme
import com.petitesroues.android.library.Browse
import com.petitesroues.android.library.CardImages
import com.petitesroues.android.library.LibraryScreen
import com.petitesroues.android.library.LibraryState
import com.petitesroues.android.pages.Section
import com.petitesroues.library.shelves.Shelves
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The library as it is drawn, so refactoring it can't change what it looks like unnoticed. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [SDK], qualifiers = PHONE)
class LibraryScreenshotTest {
  @get:Rule val compose = createComposeRule()

  private fun library(state: LibraryState, browse: Browse = Browse()) {
    val images = CardImages(ApplicationProvider.getApplicationContext())
    compose.setContent {
      AppTheme {
        LibraryScreen(
          state = state,
          images = images,
          browse = browse,
          shelvesScroll = rememberLazyGridState(),
          onBrowse = {},
          onAddFolder = {},
          onRemoveFolder = {},
          onRefresh = {},
          desktopPending = 2,
          onDesktop = {},
          onGetTitles = {},
          onAllowFiles = {},
          section = Section.LIBRARY,
          onSection = {},
          study = {},
          onOpen = {},
          onClearProgress = {},
        )
      }
    }
    compose.waitForIdle()
  }

  private val folders = listOf(Uri.parse("file:///fixtures"))

  @Test
  fun empty() {
    library(LibraryState(emptyList(), emptyList(), emptyMap(), false, emptyList(), null))
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun shelves() {
    library(
      LibraryState(
        folders,
        Fixtures.titles,
        Fixtures.progress,
        false,
        emptyList(),
        null,
        usage = "3.2 GB · 41 GB free",
      )
    )
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun series() {
    library(
      LibraryState(folders, Fixtures.titles, Fixtures.progress, false, emptyList(), null),
      Browse(
        Shelves.shelves(Fixtures.titles).flatMap { it.groups }.single { it.name == "Le phare" }.key,
        1,
      ),
    )
    compose.onRoot().captureRoboImage()
  }
}

/** A typical large phone in portrait, as the library is used. */
const val PHONE = "w411dp-h891dp-xxhdpi"

/**
 * Android 15. Robolectric's Android 16 image bundles an old commons-compress that hides the app's,
 * so the comic reader can't open a CBZ on it; on a phone the app's own copy is used.
 */
const val SDK = 35
