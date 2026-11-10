package com.petitesroues.android.comic

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petitesroues.android.Settings
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.library.TitleKind
import com.petitesroues.android.screens.Examples
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The reader's behaviour without drawing it: the example comic's real CBZ, real settings and a
 * progress database in memory.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class ReaderViewModelTest {
  @get:Rule val main = MainDispatcherRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val settings = Settings(context)
  private val database =
    Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  private val progress = ProgressStore(database.progress(), CoroutineScope(Dispatchers.Unconfined))
  private val document = Examples.document(LULU)
  private val title = Examples.title(LULU, "$LULU.cbz", TitleKind.COMIC, document)

  // Held as the title's back-stack entry holds it, so clearing it is closing the title.
  private val store = ViewModelStore()

  @After fun tearDown() = store.clear().also { database.close() }

  private fun open(saved: Progress? = null): ReaderViewModel =
    ViewModelProvider.create(
        store,
        viewModelFactory {
          initializer {
            ReaderViewModel(
              title,
              document.sourceLanguage(),
              saved,
              context,
              context.contentResolver,
              settings,
              progress,
              main.dispatcher,
            )
          }
        },
      )[ReaderViewModel::class]

  private val pages = document.comic().pages()

  @Test
  fun `opening returns to the page the comic was left at`() {
    val model = open(Progress(title.progressKey, pageId = pages[2].id()))

    assertEquals(pages[2].id(), model.state.value.pageId)
  }

  @Test
  fun `a page settling on screen is read and saved`() = runTest {
    val model = open()

    model.showing(pages[1].id(), 0.5)

    assertEquals(pages[1].id(), model.state.value.pageId)
    val saved = progress.find(title.progressKey)
    assertEquals(pages[1].id(), saved?.pageId)
    assertEquals(0.5, saved?.fraction)
  }

  @Test
  fun `pages are decoded from the CBZ, downsampled but no smaller than their slot`() = runTest {
    val model = open()
    val full = model.page(pages[0].entry(), Int.MAX_VALUE, Int.MAX_VALUE).getOrThrow()

    val image = model.page(pages[0].entry(), 200, 300).getOrThrow()

    assertTrue("${image.width}×${image.height}", image.width < full.width)
    assertTrue("${image.width}×${image.height}", image.width >= 200 && image.height >= 300)
  }

  @Test
  fun `a page missing from the CBZ says so instead of failing`() = runTest {
    assertTrue(open().page("missing.jpg", 200, 300).isFailure)
  }

  @Test
  fun `the remembered choices are read when it opens`() {
    settings.comicLandscape = true
    settings.comicOutlines = true
    settings.listeningMode = ListeningMode.LISTEN_FIRST

    val state = open().state.value

    assertEquals(ReaderUiState(null, true, true, ListeningMode.LISTEN_FIRST), state)
  }

  @Test
  fun `landscape, outlines and the listening mode are remembered`() {
    val model = open()
    val start = model.state.value.listeningMode

    model.setLandscape(true)
    model.setOutlines(true)
    val mode = model.nextListeningMode()

    assertEquals(start.next(), mode)
    assertEquals(ReaderUiState(null, true, true, mode), model.state.value)
    assertTrue(settings.comicLandscape)
    assertTrue(settings.comicOutlines)
    assertEquals(mode, settings.listeningMode)
  }

  @Test
  fun `the quiz type shared is offered next time`() {
    open().quizShared("to-translation")

    assertEquals("to-translation", open().quizType)
  }

  private companion object {
    const val LULU = "comics/lulu/le-parapluie-de-lulu"
  }
}
