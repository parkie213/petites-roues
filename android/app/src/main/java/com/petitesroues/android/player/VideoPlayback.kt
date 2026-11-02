package com.petitesroues.android.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns the media clock for one video, ported from the browser's `usePlayerPlayback`: line-end
 * pauses for auto-pause and replay, and the study cursor ([time]) that holds just inside a
 * finished line while paused so a touching next line does not appear early.
 *
 * Line ends are ExoPlayer messages at each cue's end position, delivered on the main thread every
 * time playback crosses them.
 */
class VideoPlayback(context: Context, media: Uri, val timeline: CueTimeline) {
  val player: ExoPlayer = ExoPlayer.Builder(context).build()

  /** The study cursor in seconds: the held line end while paused there, else the media position. */
  var time by mutableDoubleStateOf(0.0)
    private set

  var playing by mutableStateOf(false)
    private set

  var durationSeconds by mutableDoubleStateOf(0.0)
    private set

  /** How far through the video [position] is, from 0 to 1; null until the duration is known. */
  val fraction: Double?
    get() = if (durationSeconds > 0) (position / durationSeconds).coerceIn(0.0, 1.0) else null

  var error by mutableStateOf<String?>(null)
    private set

  var autoPause = false
  private var held: Double? = null
  private var replayEnd: Double? = null

  init {
    player.setMediaItem(MediaItem.fromUri(media))
    player.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          playing = isPlaying
          if (isPlaying) held = null
        }

        override fun onPlaybackStateChanged(state: Int) {
          if (player.duration != C.TIME_UNSET) durationSeconds = player.duration / 1000.0
        }

        override fun onPlayerError(failure: androidx.media3.common.PlaybackException) {
          error = "This phone cannot play the video: ${failure.errorCodeName}"
        }
      }
    )
    timeline.cues.forEachIndexed { index, cue ->
      player
        .createMessage { _, _ -> lineEnded(index) }
        .setLooper(Looper.getMainLooper())
        .setPosition((cue.end() * 1000).toLong())
        .setDeleteAfterDelivery(false)
        .send()
    }
    player.prepare()
  }

  private fun lineEnded(index: Int) {
    if (!player.playWhenReady) return
    val cue = timeline.cues[index]
    if (!autoPause && replayEnd != cue.end()) return
    player.pause()
    replayEnd = null
    held = CueTimeline.heldTime(cue)
    time = held!!
  }

  /** Refreshes [time] from the player; called on every animation frame tick. */
  fun tick() {
    time = held ?: (player.currentPosition / 1000.0)
  }

  /** The position to save and restore: the held line end if paused there. */
  val position: Double
    get() = held ?: (player.currentPosition / 1000.0)

  fun play() {
    if (player.playbackState == Player.STATE_ENDED) seek(0.0)
    player.play()
  }

  fun pause() = player.pause()

  fun toggle() = if (player.playWhenReady) pause() else play()

  fun seek(seconds: Double) {
    held = null
    replayEnd = null
    val clamped = seconds.coerceAtLeast(0.0)
    player.seekTo((clamped * 1000).toLong())
    time = clamped
  }

  /** Plays one line from its start and pauses at its end, whatever the auto-pause setting. */
  fun replay(index: Int) {
    val cue = timeline.cues.getOrNull(index) ?: return
    seek(cue.start())
    replayEnd = cue.end()
    player.play()
  }

  /** Moves to the previous (-1) or next (+1) line's start, keeping the play state. */
  fun step(direction: Int) {
    timeline.stepTarget(time, direction)?.let(::seek)
  }

  fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

  fun release() = player.release()
}
