package app.petitesroues.android.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.petitesroues.android.Settings
import app.petitesroues.android.data.Progress
import app.petitesroues.android.data.ProgressStore
import app.petitesroues.android.library.Title
import app.petitesroues.model.document.StudyDocument
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The player's remembered choices, owned by [PlayerViewModel]: line-end auto-pause, the playback
 * speed, whether source subtitles show, and which side the controls are on.
 */
data class PlayerUiState(
  val autoPause: Boolean,
  val speed: Float,
  val subtitles: Boolean,
  val controlsLeft: Boolean,
)

/**
 * Plays [title]'s video: owns its [VideoPlayback], starting where it was left ([saved]), and saves
 * the position every few seconds while playing, on each pause and when the title is closed. One
 * lives as long as its title screen is on the back stack, so the video, its position and its buffer
 * survive the activity being recreated. Playback's clock is Compose state on [playback], read every
 * frame; the remembered choices are [state].
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel
@AssistedInject
constructor(
  @Assisted val title: Title,
  @Assisted val document: StudyDocument,
  @Assisted saved: Progress?,
  @ApplicationContext context: Context,
  private val settings: Settings,
  private val progress: ProgressStore,
) : ViewModel() {
  /** Creates the ViewModel for an opened video. */
  @AssistedFactory
  interface Factory {
    fun create(title: Title, document: StudyDocument, saved: Progress?): PlayerViewModel
  }

  val timeline = CueTimeline(document.video().cues())
  val playback = VideoPlayback(context, checkNotNull(title.mediaUri), timeline)

  private val _state =
    MutableStateFlow(
      PlayerUiState(
        settings.autoPause,
        settings.playbackSpeed,
        settings.showSubtitles,
        settings.controlsOnLeft,
      )
    )

  /** The remembered choices. */
  val state: StateFlow<PlayerUiState> = _state.asStateFlow()

  /** The quiz type last shared from any title. */
  val quizType: String
    get() = settings.quizType

  private var saving: Job? = null

  // Saves every few seconds while playing, and on each pause.
  private val saver =
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        saving?.cancel()
        if (isPlaying)
          saving = viewModelScope.launch {
            while (isActive) {
              delay(SAVE_EVERY_MS)
              save()
            }
          }
        else save()
      }
    }

  init {
    saved?.positionSeconds?.let(playback::seek)
    playback.autoPause = _state.value.autoPause
    playback.setSpeed(_state.value.speed)
    playback.player.addListener(saver)
  }

  fun setAutoPause(on: Boolean) {
    settings.autoPause = on
    playback.autoPause = on
    _state.update { it.copy(autoPause = on) }
  }

  fun setSpeed(speed: Float) {
    settings.playbackSpeed = speed
    playback.setSpeed(speed)
    _state.update { it.copy(speed = speed) }
  }

  fun toggleSubtitles() {
    val on = !_state.value.subtitles
    settings.showSubtitles = on
    _state.update { it.copy(subtitles = on) }
  }

  fun swapSides() {
    val left = !_state.value.controlsLeft
    settings.controlsOnLeft = left
    _state.update { it.copy(controlsLeft = left) }
  }

  /** Remembers [type] as the quiz type to offer next. */
  fun quizShared(type: String) {
    settings.quizType = type
  }

  private fun save() =
    progress.savePosition(title.progressKey, playback.position, playback.fraction)

  override fun onCleared() {
    playback.player.removeListener(saver)
    save()
    playback.release()
  }

  private companion object {
    const val SAVE_EVERY_MS = 5_000L
  }
}
