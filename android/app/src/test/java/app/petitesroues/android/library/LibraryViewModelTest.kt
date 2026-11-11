package app.petitesroues.android.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.petitesroues.android.Settings
import app.petitesroues.android.data.IndexDatabase
import app.petitesroues.android.data.Progress
import app.petitesroues.android.data.ProgressDatabase
import app.petitesroues.android.data.ProgressStore
import app.petitesroues.android.desktop.DesktopFinder
import app.petitesroues.android.desktop.PairedDesktop
import app.petitesroues.android.desktop.Transfers
import app.petitesroues.android.pages.MarkdownPageScanner
import app.petitesroues.android.pages.Section
import app.petitesroues.android.screens.Examples
import app.petitesroues.android.screens.SDK
import app.petitesroues.android.testing.FakeAppFolder
import app.petitesroues.android.testing.FakeDesktop
import app.petitesroues.android.testing.FakeNsd
import app.petitesroues.android.testing.MainDispatcherRule
import app.petitesroues.documents.StudyDocumentCodec
import app.petitesroues.documents.StudyDocumentJson
import app.petitesroues.documents.StudyDocumentReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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
 * The library's behaviour, without drawing it: a real index and progress database in memory, and a
 * real [LibraryService] refreshing the repository's `example/` folders.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class LibraryViewModelTest {
  @get:Rule val main = MainDispatcherRule()
  @get:Rule val downloads = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val documents = Examples.folder("documents-fr")
  private val media = Examples.folder("media-fr")

  private lateinit var indexDatabase: IndexDatabase
  private lateinit var progressDatabase: ProgressDatabase
  private lateinit var settings: Settings
  private lateinit var folders: Folders
  private lateinit var library: LibraryService
  private val desktop = FakeDesktop()
  /** No All files access until a test grants it. */
  private val appFolder = FakeAppFolder()

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
    settings = Settings(context).apply { contentTrees = listOf(documents, media) }
    folders = Folders(settings, appFolder)
    library =
      LibraryService(
        context.contentResolver,
        indexDatabase.index(),
        StudyDocumentReader(StudyDocumentCodec(StudyDocumentJson.newMapper())),
      )
  }

  @After
  fun tearDown() {
    indexDatabase.close()
    progressDatabase.close()
  }

  private fun viewModel() =
    LibraryViewModel(
      context,
      settings,
      folders,
      indexDatabase.index(),
      ProgressStore(progressDatabase.progress(), CoroutineScope(Dispatchers.Unconfined)),
      library,
      CardImages(context),
      MarkdownPageScanner(context.contentResolver),
      context.contentResolver,
      Transfers(
        context,
        settings,
        folders,
        desktop,
        PairedDesktop(settings, DesktopFinder(FakeNsd())),
      ),
      main.dispatcher,
      SavedStateHandle(),
    )

  /** Skips states until one matches [done], and returns it. */
  private suspend fun ReceiveTurbine<LibraryUiState>.awaitUntil(
    done: (LibraryUiState) -> Boolean
  ): LibraryUiState {
    while (true) awaitItem().let { if (done(it)) return it }
  }

  private val LibraryUiState.documentIds
    get() = titles.map { it.documentId }.toSet()

  /**
   * The example's titles. The s01e02 document references no file (the desktop's getting-started
   * tour links it by hand, which the phone doesn't do), so its video has a card of its own.
   */
  private val examples =
    setOf(
      "lulu-example-le-parapluie",
      "peppa-pig-example-s01e01",
      "peppa-pig-example-s01e02",
      "media:media-fr/series/peppa-pig/s01e02-a-la-recherche-de-mr-dinosaure.mp4",
    )

  /** Waits for the first state that matches [done]: some of the work runs on other threads. */
  private suspend fun LibraryViewModel.awaitState(done: (LibraryUiState) -> Boolean) =
    state.first(done)

  @Test
  fun `refresh shows that it is running, then the folders' titles`() = runTest {
    val model = viewModel()
    model.state.test {
      assertTrue(awaitItem().titles.isEmpty())
      model.refresh()
      val running = awaitItem()
      assertTrue(running.refreshing)
      assertEquals(folders.library, running.folders)
      val done = awaitUntil { !it.refreshing }
      assertEquals(examples, done.documentIds)
      assertNull(done.refreshProgress)
      assertNull(done.message)
    }
    assertTrue(settings.libraryBytes!! > 0)
  }

  @Test
  fun `titles kept in the app's own folder are listed once it is allowed`() = runTest {
    // The app's own folder holds the example videos; the only chosen folder, the documents.
    settings.contentTrees = listOf(documents)
    val model = viewModel()
    model.refresh()
    assertFalse(model.awaitState { !it.refreshing }.titles.any { it.mediaUri != null })

    appFolder.folder = media
    model.refresh()
    val state = model.awaitState { !it.refreshing && it.folders.size == 2 }
    assertEquals(listOf(media, documents), state.folders)
    assertEquals(examples, state.documentIds)
  }

  @Test
  fun `showing an empty library with folders refreshes it`() = runTest {
    val model = viewModel()
    model.onShown()
    assertEquals(examples, model.awaitState { it.titles.isNotEmpty() }.documentIds)
  }

  @Test
  fun `showing an indexed library only reads the index`() = runTest {
    library.refresh(listOf(documents, media))
    val model = viewModel()
    model.state.test {
      awaitItem()
      model.onShown()
      val shown = awaitItem()
      assertEquals(examples, shown.documentIds)
      assertFalse(shown.refreshing)
      expectNoEvents()
    }
  }

  @Test
  fun `a folder that can't be read is reported, and the others still load`() = runTest {
    val missing = Uri.fromFile(context.filesDir.resolve("no-such-folder"))
    settings.contentTrees = listOf(documents, media, missing)
    val model = viewModel()
    model.refresh()
    val state = model.awaitState { !it.refreshing }
    assertEquals(examples, state.documentIds)
    assertEquals(1, state.problems.size)
  }

  @Test
  fun `a failed refresh says why and stops`() = runTest {
    val model = viewModel()
    indexDatabase.close()
    model.refresh()
    val state = model.awaitState { !it.refreshing }
    val message = checkNotNull(state.message)
    assertTrue(message, message.startsWith("Refresh failed"))
  }

  @Test
  fun `removing a folder forgets it and refreshes without it`() = runTest {
    val model = viewModel()
    model.refresh()
    model.awaitState { !it.refreshing }
    model.removeFolder(media)
    assertEquals(listOf(documents), settings.contentTrees)
    val state = model.awaitState { !it.refreshing }
    assertEquals(folders.library, state.folders)
    assertFalse(media in state.folders)
    assertFalse(state.titles.any { it.mediaUri != null })
  }

  @Test
  fun `clearing a title's progress forgets where it was left`() = runTest {
    progressDatabase.progress().save(Progress("peppa-pig-example-s01e01", 30.0, fraction = 0.5))
    val model = viewModel()
    model.onShown()
    val shown = model.awaitState { it.titles.isNotEmpty() && it.progress.isNotEmpty() }
    val title = shown.titles.single { it.documentId == "peppa-pig-example-s01e01" }
    assertEquals(0.5, shown.progress[title.progressKey]?.fraction)
    model.clearProgress(title)
    model.awaitState { title.progressKey !in it.progress }
  }

  @Test
  fun `Notes reads the folders' notes, and the library tab has no pages`() = runTest {
    // Pages sit in each repository's notes/ folder, one level inside a content folder.
    settings.contentTrees = listOf(Examples.folder(""))
    val model = viewModel()
    model.selectSection(Section.NOTES)
    assertEquals(Section.NOTES, model.section.value)
    assertEquals(listOf("Verbs followed by an infinitive"), model.pages.value?.map { it.title })
    model.selectSection(Section.LIBRARY)
    assertNull(model.pages.value)
  }

  @Test
  fun `a paired desktop is checked for what a sync would fetch`() = runTest {
    desktop.titles = listOf(FakeDesktop.comic("Lulu", 1), FakeDesktop.comic("Lulu", 2))
    settings.desktop = FakeDesktop.DESKTOP
    settings.downloadFolder = Uri.fromFile(downloads.root)
    settings.followed = setOf("Lulu")

    val model = viewModel()

    assertTrue(model.paired)
    assertEquals(2, model.desktopPending.first { it > 0 })
  }

  @Test
  fun `an unpaired phone has nothing to fetch`() {
    val model = viewModel()

    assertFalse(model.paired)
    assertFalse(model.desktopReachable)
    assertEquals(0, model.desktopPending.value)
  }
}
