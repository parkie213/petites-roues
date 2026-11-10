package com.petitesroues.android.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petitesroues.android.Settings
import com.petitesroues.android.data.IndexDao
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.desktop.Transfers
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.android.pages.MarkdownPage
import com.petitesroues.android.pages.MarkdownPageScanner
import com.petitesroues.android.pages.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The library and what can be done to it: reading the index and progress, refreshing from the
 * content folders, adding and removing folders, clearing a title's progress, and the pages behind
 * the Notes and Quiz me tabs. The library screen draws [state], [section] and [pages] and calls
 * back here; the work runs in this ViewModel's scope, so it outlives recomposition and is tested
 * without drawing anything.
 */
@HiltViewModel
class LibraryViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val settings: Settings,
  private val index: IndexDao,
  private val progress: ProgressStore,
  private val library: LibraryService,
  private val images: CardImages,
  private val pageScanner: MarkdownPageScanner,
  private val resolver: ContentResolver,
  @IoDispatcher private val io: CoroutineDispatcher,
  private val saved: SavedStateHandle,
) : ViewModel() {
  private val _state =
    MutableStateFlow(
      LibraryUiState(settings.libraryFolders, emptyList(), emptyMap(), false, emptyList(), null)
    )

  /** The titles, their progress and the state of any refresh. */
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  /** The open tab: the library, Notes or Quiz me. Kept across process death. */
  val section: StateFlow<Section> = saved.getStateFlow(SECTION, Section.LIBRARY)

  private val _pages = MutableStateFlow<List<MarkdownPage>?>(null)

  /** The open Notes or Quiz me tab's pages; null while they are read, or on the library tab. */
  val pages: StateFlow<List<MarkdownPage>?> = _pages.asStateFlow()

  private var reading: Job? = null

  init {
    // Each title copied from the desktop shows up once the library has refreshed.
    viewModelScope.launch {
      Transfers.status
        .map { it.finished }
        .distinctUntilChanged()
        .filter { it > 0 }
        .collect { refresh() }
    }
  }

  /**
   * The library is on screen: re-reads the index and progress, which a title just watched or read
   * has changed, and the open tab's pages. An empty index with folders to read (a first launch, or
   * an update that changed the index's schema, which drops it) is rebuilt straight away.
   */
  fun onShown() {
    viewModelScope.launch {
      reload()
      val now = _state.value
      if (now.titles.isEmpty() && now.folders.isNotEmpty() && !now.refreshing) refresh()
    }
    readPages()
  }

  /**
   * Rescans every content folder into the index, then reloads. Progress is reported while it runs;
   * a failure is reported in [LibraryUiState.message] and leaves the previous titles.
   */
  fun refresh() {
    val trees = settings.libraryFolders
    _state.update { it.copy(folders = trees, refreshing = true, message = null) }
    viewModelScope.launch {
      try {
        val started = System.nanoTime()
        val refreshed =
          withContext(io) {
            library
              .refresh(trees) { found -> _state.update { it.copy(refreshProgress = found) } }
              .also { images.clear() }
          }
        Log.i(
          "Library",
          "Refreshed ${trees.size} folders in ${(System.nanoTime() - started) / 1_000_000} ms",
        )
        settings.libraryBytes = refreshed.bytes
        _state.update { it.copy(problems = refreshed.problems) }
        reload()
      } catch (failure: CancellationException) {
        throw failure
      } catch (failure: Exception) {
        _state.update { it.copy(message = "Refresh failed: ${failure.message}") }
      } finally {
        _state.update { it.copy(refreshing = false, refreshProgress = null) }
      }
    }
  }

  /** Adds [tree], a folder the user just granted access to, and refreshes. */
  fun addFolder(tree: Uri) {
    resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (tree !in settings.contentTrees) settings.contentTrees += tree
    refresh()
  }

  /** Forgets [tree] and the access granted to it, and refreshes without it. */
  fun removeFolder(tree: Uri) {
    settings.contentTrees -= tree
    if (settings.downloadFolder == tree) settings.downloadFolder = null
    // A folder chosen for downloads was granted for writing too.
    runCatching {
      resolver.releasePersistableUriPermission(
        tree,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
      .recoverCatching {
        resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    refresh()
  }

  /** Forgets where [title] was left, so it starts from the beginning. */
  fun clearProgress(title: Title) {
    viewModelScope.launch {
      progress.clear(title.progressKey)
      reload()
    }
  }

  /** Opens the library, Notes or Quiz me; Notes and Quiz me read their pages each time. */
  fun selectSection(section: Section) {
    saved[SECTION] = section
    readPages()
  }

  private suspend fun reload() {
    val titles = withContext(io) { index.titles() }
    val saved = progress.all()
    val usage =
      withContext(io) {
        runCatching {
          LibraryUsage.line(
            settings.libraryBytes,
            LibraryUsage.free(context, settings.libraryFolders),
          )
        }
          .getOrNull()
          ?.ifBlank { null }
      }
    _state.update { it.copy(titles = titles, progress = saved, usage = usage) }
  }

  private fun readPages() {
    reading?.cancel()
    _pages.value = null
    val folder = section.value.folder ?: return
    reading = viewModelScope.launch {
      _pages.value = withContext(io) { pageScanner.scan(settings.libraryFolders, folder) }
    }
  }

  private companion object {
    const val SECTION = "section"
  }
}
