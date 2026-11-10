package com.petitesroues.android.comic

import android.content.ContentResolver
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import com.petitesroues.android.Settings
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.android.library.Title
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Where the reader is and its remembered choices, owned by [ReaderViewModel].
 *
 * @param pageId the page being read, by its id, which a pages-only comic keeps when its direction
 *   flips; null for the first page
 * @param landscape whether reading is locked to landscape
 * @param outlines whether bubble outlines are shown
 * @param listeningMode whether tapping a bubble reads it aloud, opens its card, or both
 */
data class ReaderUiState(
  val pageId: String?,
  val landscape: Boolean,
  val outlines: Boolean,
  val listeningMode: ListeningMode,
)

/**
 * Reads [title]'s comic: owns its open CBZ and the read-aloud voice for [language], keeps the page
 * being read and saves it as each page settles, and holds the remembered choices. One lives as long
 * as its title screen is on the back stack, so the page, the archive and the voice survive the
 * activity being recreated. The document itself comes from the title screen, since flipping a
 * pages-only comic's direction rebuilds it.
 */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel
@AssistedInject
constructor(
  @Assisted val title: Title,
  @Assisted language: String,
  @Assisted saved: Progress?,
  @ApplicationContext context: Context,
  resolver: ContentResolver,
  private val settings: Settings,
  private val progress: ProgressStore,
  @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
  /** Creates the ViewModel for an opened comic, read aloud in its source [language]. */
  @AssistedFactory
  interface Factory {
    fun create(title: Title, language: String, saved: Progress?): ReaderViewModel
  }

  private val archive = CbzReader(resolver, checkNotNull(title.mediaUri) { "its CBZ is missing" })

  /** The voice that reads bubbles aloud. */
  val readAloud = ReadAloud(context, language)

  private val _state =
    MutableStateFlow(
      ReaderUiState(
        saved?.pageId,
        settings.comicLandscape,
        settings.comicOutlines,
        settings.listeningMode,
      )
    )

  /** The page and the remembered choices. */
  val state: StateFlow<ReaderUiState> = _state.asStateFlow()

  /** The quiz type last shared from any title. */
  val quizType: String
    get() = settings.quizType

  /** The page [entry] decoded to fit [maxWidth] by [maxHeight] pixels, or why it couldn't be. */
  suspend fun page(entry: String, maxWidth: Int, maxHeight: Int): Result<ImageBitmap> =
    withContext(io) { runCatching { archive.page(entry, maxWidth, maxHeight) } }

  /** The page [pageId] has settled on screen, [fraction] of the way through: it is saved. */
  fun showing(pageId: String, fraction: Double) {
    _state.update { it.copy(pageId = pageId) }
    progress.savePage(title.progressKey, pageId, fraction)
  }

  fun setLandscape(on: Boolean) {
    settings.comicLandscape = on
    _state.update { it.copy(landscape = on) }
  }

  fun setOutlines(on: Boolean) {
    settings.comicOutlines = on
    _state.update { it.copy(outlines = on) }
  }

  /** Moves to the next listening mode, stopping anything being read. */
  fun nextListeningMode(): ListeningMode {
    val mode = _state.value.listeningMode.next()
    settings.listeningMode = mode
    readAloud.stop()
    _state.update { it.copy(listeningMode = mode) }
    return mode
  }

  /** Remembers [type] as the quiz type to offer next. */
  fun quizShared(type: String) {
    settings.quizType = type
  }

  override fun onCleared() {
    archive.close()
    readAloud.shutdown()
  }
}
