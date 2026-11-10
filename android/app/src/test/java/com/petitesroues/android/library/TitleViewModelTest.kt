package com.petitesroues.android.library

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.petitesroues.android.Settings
import com.petitesroues.android.data.IndexDatabase
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.screens.Examples
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.MainDispatcherRule
import com.petitesroues.documents.StudyDocumentReader
import com.petitesroues.model.document.ReadingDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opening the repository's `example/` titles, with progress in a real database in memory. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class TitleViewModelTest {
  @get:Rule val main = MainDispatcherRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var indexDatabase: IndexDatabase
  private lateinit var progressDatabase: ProgressDatabase
  private lateinit var settings: Settings

  private val episode =
    Examples.document(EPISODE).let {
      Examples.title(
        EPISODE,
        "series/peppa-pig/s01e01-les-flaques-de-boue.mp4",
        TitleKind.VIDEO,
        it,
      )
    }
  private val comic =
    Examples.document(COMIC).let { Examples.title(COMIC, "$COMIC.cbz", TitleKind.COMIC, it) }

  /** The example comic as if it had no study document: it reads from its CBZ alone. */
  private val pagesOnly = comic.copy(path = "comics/lulu/pages-only", documentUri = null)

  @Before
  fun setUp() {
    indexDatabase =
      Room.inMemoryDatabaseBuilder(context, IndexDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    progressDatabase =
      Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    settings = Settings(context)
  }

  @After
  fun tearDown() {
    indexDatabase.close()
    progressDatabase.close()
  }

  private fun viewModel() =
    TitleViewModel(
      settings,
      ProgressStore(progressDatabase.progress(), CoroutineScope(Dispatchers.Unconfined)),
      LibraryService(context.contentResolver, indexDatabase.index(), StudyDocumentReader()),
      context.contentResolver,
      main.dispatcher,
    )

  private suspend fun TitleViewModel.awaitReady() =
    state.first { it is TitleUiState.Ready || it is TitleUiState.Failed } as TitleUiState.Ready

  @Test
  fun `an episode opens with its document and where it was left`() = runTest {
    progressDatabase.progress().save(Progress(episode.progressKey, positionSeconds = 30.0))
    val model = viewModel()
    model.state.test {
      assertEquals(TitleUiState.Closed, awaitItem())
      model.open(episode)
      assertEquals(TitleUiState.Opening(episode), awaitItem())
      val ready = awaitItem() as TitleUiState.Ready
      assertEquals("peppa-pig-example-s01e01", ready.document.id())
      assertEquals(30.0, ready.saved?.positionSeconds)
    }
  }

  @Test
  fun `a comic opens with its document on the page it was left at`() = runTest {
    progressDatabase.progress().save(Progress(comic.progressKey, pageId = "page-2"))
    val model = viewModel()
    model.open(comic)
    val ready = model.awaitReady()
    assertEquals("lulu-example-le-parapluie", ready.document.id())
    assertEquals("page-2", ready.saved?.pageId)
  }

  @Test
  fun `a title never opened has no progress`() = runTest {
    val model = viewModel()
    model.open(episode)
    assertNull(model.awaitReady().saved)
  }

  @Test
  fun `a comic without a document reads its CBZ's pages, left to right at first`() = runTest {
    val model = viewModel()
    model.open(pagesOnly)
    val ready = model.awaitReady()
    assertEquals(4, ready.document.comic().pages().size)
    assertEquals(ReadingDirection.LTR, ready.document.comic().readingDirection())
  }

  @Test
  fun `flipping a pages-only comic rebuilds it without closing the reader, and is remembered`() =
    runTest {
      val model = viewModel()
      model.open(pagesOnly)
      model.awaitReady()
      model.state.test {
        awaitItem()
        model.flipDirection()
        // Straight from one ready document to the other: no Opening in between.
        val flipped = awaitItem() as TitleUiState.Ready
        assertTrue(flipped.rtl)
        assertEquals(ReadingDirection.RTL, flipped.document.comic().readingDirection())
      }
      assertTrue(settings.comicRtl(pagesOnly.path))
    }

  @Test
  fun `a pages-only comic opens in the direction chosen for it`() = runTest {
    settings.setComicRtl(pagesOnly.path, true)
    val model = viewModel()
    model.open(pagesOnly)
    assertTrue(model.awaitReady().rtl)
  }

  @Test
  fun `a document that can't be read says why`() = runTest {
    val missing = episode.copy(path = "missing", documentUri = Uri.parse("file:///no/such.json"))
    val model = viewModel()
    model.open(missing)
    val failed =
      model.state.first { it is TitleUiState.Failed || it is TitleUiState.Ready }
        as TitleUiState.Failed
    assertEquals(missing, failed.title)
  }

  @Test
  fun `opening the open title again keeps it, and closing forgets it`() = runTest {
    val model = viewModel()
    model.open(episode)
    val ready = model.awaitReady()
    model.state.test {
      awaitItem()
      model.open(episode)
      expectNoEvents()
      model.close()
      assertEquals(TitleUiState.Closed, awaitItem())
    }
    assertEquals("peppa-pig-example-s01e01", ready.document.id())
  }

  private companion object {
    const val EPISODE = "series/peppa-pig/s01e01"
    const val COMIC = "comics/lulu/le-parapluie-de-lulu"
  }
}
