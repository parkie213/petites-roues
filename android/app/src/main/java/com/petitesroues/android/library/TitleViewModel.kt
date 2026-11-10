package com.petitesroues.android.library

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petitesroues.android.Settings
import com.petitesroues.android.comic.CbzReader
import com.petitesroues.android.comic.bareComic
import com.petitesroues.android.data.IndexDao
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.model.document.StudyDocument
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the title screen shows: "Opening title…", why the title couldn't be opened, or the player or
 * reader for it. This is the screen's state; a [Title] itself is an unchanging record.
 */
sealed interface TitleUiState {
  /** The title's document and progress are being read. */
  data object Opening : TitleUiState

  /** The title named [name] could not be opened, for the reason in [message]. */
  data class Failed(val name: String, val message: String?) : TitleUiState

  /**
   * [title] is ready to watch or read: its [document], where it was left ([saved], or null), and
   * for a pages-only comic the reading direction it was built in ([rtl]).
   */
  data class Ready(
    val title: Title,
    val document: StudyDocument,
    val saved: Progress?,
    val rtl: Boolean,
  ) : TitleUiState
}

/**
 * Opens the title at [path] for the player or the reader: finds it in the index, reads its study
 * document (or builds a pages-only one for a comic without), and where it was left. One lives as
 * long as its title screen is on the back stack, so reopening a title reads its progress afresh.
 */
@HiltViewModel(assistedFactory = TitleViewModel.Factory::class)
class TitleViewModel
@AssistedInject
constructor(
  @Assisted private val path: String,
  private val index: IndexDao,
  private val settings: Settings,
  private val progress: ProgressStore,
  private val library: LibraryService,
  private val resolver: ContentResolver,
  @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
  /** Creates the ViewModel for the title at a path. */
  @AssistedFactory
  interface Factory {
    fun create(path: String): TitleViewModel
  }

  private val _state = MutableStateFlow<TitleUiState>(TitleUiState.Opening)

  /** Where opening the title has got to. */
  val state: StateFlow<TitleUiState> = _state.asStateFlow()

  private var loading: Job = viewModelScope.launch {
    val title = withContext(io) { index.title(path) }
    if (title == null) _state.value = TitleUiState.Failed(path, "it is no longer in the library")
    else load(title, rtl = title.pagesOnly && settings.comicRtl(path), saved = null)
  }

  /**
   * Rebuilds the open pages-only comic in the other reading direction, and remembers it. The
   * current document stays until the new one is ready, so the reader keeps its page.
   */
  fun flipDirection() {
    val ready = _state.value as? TitleUiState.Ready ?: return
    if (!ready.title.pagesOnly) return
    val rtl = !ready.rtl
    settings.setComicRtl(path, rtl)
    loading.cancel()
    loading = viewModelScope.launch { load(ready.title, rtl, ready.saved) }
  }

  /** Reads [title]'s document; [saved] is the progress already read, or null to read it. */
  private suspend fun load(title: Title, rtl: Boolean, saved: Progress?) {
    _state.value =
      try {
        val left = saved ?: progress.find(title.progressKey)
        TitleUiState.Ready(title, withContext(io) { document(title, rtl) }, left, rtl)
      } catch (failure: CancellationException) {
        throw failure
      } catch (failure: Exception) {
        TitleUiState.Failed(title.name, failure.message)
      }
  }

  private fun document(title: Title, rtl: Boolean): StudyDocument =
    if (title.pagesOnly)
      CbzReader(resolver, checkNotNull(title.mediaUri) { "its CBZ is missing" }).use {
        bareComic(title, it.pages(), rtl)
      }
    else library.read(checkNotNull(title.documentUri) { "it has no study document" })
}
