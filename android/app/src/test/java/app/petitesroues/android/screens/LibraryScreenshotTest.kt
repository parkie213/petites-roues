package app.petitesroues.android.screens

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.android.AppTheme
import app.petitesroues.android.library.Browse
import app.petitesroues.android.library.CardImages
import app.petitesroues.android.library.CardSource
import app.petitesroues.android.library.LibraryScreen
import app.petitesroues.android.library.LibraryUiState
import app.petitesroues.android.pages.MarkdownPage
import app.petitesroues.android.pages.Section
import app.petitesroues.android.pages.StudyPages
import app.petitesroues.library.shelves.Shelves
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
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

  private fun library(
    state: LibraryUiState,
    browse: Browse = Browse(),
    section: Section = Section.LIBRARY,
    pages: List<MarkdownPage> = emptyList(),
  ) {
    val images = CardImages(ApplicationProvider.getApplicationContext())
    // Cards decode their images on a background thread; load them first so they're drawn.
    runBlocking {
      images.load(CardSource.Poster(Fixtures.poster))
      images.load(CardSource.Page(Fixtures.comic, null))
    }
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
          section = section,
          onSection = {},
          study = {
            StudyPages(
              section,
              pages,
              remembered = null,
              onBack = {},
              onOpened = {},
              onChat = { _, _ -> },
              onCopy = {},
            )
          },
          onOpen = {},
          onClearProgress = {},
        )
      }
    }
    compose.waitForIdle()
  }

  private val folders = listOf(Uri.parse("file:///fixtures"))

  private val stocked =
    LibraryUiState(folders, Fixtures.titles, Fixtures.progress, false, emptyList(), null)

  @Test
  fun empty() {
    library(LibraryUiState(emptyList(), emptyList(), emptyMap(), false, emptyList(), null))
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun shelves() {
    library(
      LibraryUiState(
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
      stocked,
      Browse(
        Shelves.shelves(Fixtures.titles).flatMap { it.groups }.single { it.name == "Le phare" }.key,
        1,
      ),
    )
    compose.onRoot().captureRoboImage()
  }

  /**
   * Opens [page] from the list. Its Markdown is parsed on a background thread, so this waits until
   * [bodyText], a line from its body, is drawn.
   */
  private fun open(page: MarkdownPage, bodyText: String) {
    compose.onNodeWithText(page.title).performClick()
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.onAllNodesWithText(bodyText).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun notes() {
    library(stocked, section = Section.NOTES, pages = Fixtures.notes)
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun note() {
    library(stocked, section = Section.NOTES, pages = Fixtures.notes)
    open(Fixtures.notes.first(), "Only the first verb is conjugated")
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun quizzes() {
    library(stocked, section = Section.QUIZZES, pages = Fixtures.quizzes)
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun quiz() {
    library(stocked, section = Section.QUIZZES, pages = Fixtures.quizzes)
    open(Fixtures.quizzes.first(), "Exercise")
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
