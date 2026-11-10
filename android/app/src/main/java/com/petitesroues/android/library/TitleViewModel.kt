package com.petitesroues.android.library

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petitesroues.android.Settings
import com.petitesroues.android.comic.CbzReader
import com.petitesroues.android.comic.bareComic
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.model.document.StudyDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where opening a title has got to. */
sealed interface TitleState {
  /** No title is open. */
  data object Closed : TitleState

  /** [title]'s document and progress are being read. */
  data class Opening(val title: Title) : TitleState

  /** [title] could not be opened, for the reason in [message]. */
  data class Failed(val title: Title, val message: String?) : TitleState

  /**
   * [title] is ready to watch or read: its [document], where it was left ([saved], or null), and
   * for a pages-only comic the reading direction it was built in ([rtl]).
   */
  data class Ready(
    val title: Title,
    val document: StudyDocument,
    val saved: Progress?,
    val rtl: Boolean,
  ) : TitleState
}

/**
 * A comic with no study document: it reads from a pages-only document built from its CBZ, in the
 * direction chosen for it.
 */
val Title.pagesOnly: Boolean
  get() = documentUri == null && kind == TitleKind.COMIC

/**
 * Opens a title for the player or the reader: reads its study document (or builds a pages-only one
 * for a comic without), and where it was left. Opening the title that is already open does nothing,
 * so the open title survives the activity being recreated; [close] forgets it, so the next open
 * reads progress afresh.
 */
@HiltViewModel
class TitleViewModel
@Inject
constructor(
  private val settings: Settings,
  private val progress: ProgressStore,
  private val library: LibraryService,
  private val resolver: ContentResolver,
  @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
  private val _state = MutableStateFlow<TitleState>(TitleState.Closed)

  /** The open title, or where opening it has got to. */
  val state: StateFlow<TitleState> = _state.asStateFlow()

  private var loading: Job? = null

  /** Opens [title], unless it is already open. */
  fun open(title: Title) {
    if (_state.value.path == title.path) return
    _state.value = TitleState.Opening(title)
    load(title, rtl = title.pagesOnly && settings.comicRtl(title.path), saved = null)
  }

  /** Closes the open title. */
  fun close() {
    loading?.cancel()
    _state.value = TitleState.Closed
  }

  /**
   * Rebuilds the open pages-only comic in the other reading direction, and remembers it. The
   * current document stays until the new one is ready, so the reader keeps its page.
   */
  fun flipDirection() {
    val ready = _state.value as? TitleState.Ready ?: return
    if (!ready.title.pagesOnly) return
    val rtl = !ready.rtl
    settings.setComicRtl(ready.title.path, rtl)
    load(ready.title, rtl, ready.saved)
  }

  /** Reads [title]'s document; [saved] is the progress already read, or null to read it. */
  private fun load(title: Title, rtl: Boolean, saved: Progress?) {
    loading?.cancel()
    loading = viewModelScope.launch {
      _state.value =
        try {
          val left = saved ?: progress.find(title.progressKey)
          TitleState.Ready(title, withContext(io) { document(title, rtl) }, left, rtl)
        } catch (failure: CancellationException) {
          throw failure
        } catch (failure: Exception) {
          TitleState.Failed(title, failure.message)
        }
    }
  }

  private fun document(title: Title, rtl: Boolean): StudyDocument =
    if (title.pagesOnly)
      CbzReader(resolver, checkNotNull(title.mediaUri) { "its CBZ is missing" }).use {
        bareComic(title, it.pages(), rtl)
      }
    else library.read(checkNotNull(title.documentUri) { "it has no study document" })

  private val TitleState.path: String?
    get() =
      when (this) {
        TitleState.Closed -> null
        is TitleState.Opening -> title.path
        is TitleState.Failed -> title.path
        is TitleState.Ready -> title.path
      }
}
