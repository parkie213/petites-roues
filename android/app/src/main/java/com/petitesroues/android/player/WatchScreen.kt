package com.petitesroues.android.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.ui.compose.ContentFrame
import androidx.compose.material3.LocalContentColor
import com.petitesroues.android.Settings
import com.petitesroues.android.ThinSlider
import com.petitesroues.android.library.LibraryStore
import com.petitesroues.android.library.Title
import com.petitesroues.android.quiz.QuizBadge
import com.petitesroues.android.quiz.QuizDialog
import com.petitesroues.android.quiz.QuizPack
import com.petitesroues.android.quiz.QuizShare
import com.petitesroues.android.study.readyAlignment
import com.petitesroues.model.ChunkSeparator
import com.petitesroues.android.study.LearnerNote
import com.petitesroues.android.study.PhraseLine
import com.petitesroues.android.study.SubtitleLook
import com.petitesroues.android.study.TranslationContext
import com.petitesroues.android.study.phraseNotes
import com.petitesroues.model.document.StudyDocument
import kotlin.math.abs

private val speeds = listOf(0.5f, 0.6f, 0.8f, 1f, 1.25f, 1.5f)

private enum class Help {
  TRANSLATION,
  NOTES,
}

/** A phrase being explored: the cue it belongs to and its source phrase index. */
private data class Explored(val cueId: String, val phrase: Int)

private val subtitleStyle =
  TextStyle(
    color = Color.White,
    fontSize = 21.sp,
    lineHeight = 28.sp,
    shadow = Shadow(Color.Black, blurRadius = 6f),
  )
private val panelStyle = TextStyle(color = Color(0xFFE0E2E6), fontSize = 15.sp, lineHeight = 22.sp)
private val panelBackground = Color(0xF520242A)
private val railButton = 42.dp
private val railPadding = 5.dp
private val railMargin = 12.dp

/**
 * The full-screen landscape player: the video with tappable phrase subtitles, a pill of line
 * shortcuts and study toggles on the right, and transport controls when the learner pauses (not
 * when auto-pause or a phrase tap stops the video). Tapping the video plays or pauses; swiping it
 * steps between lines like the previous/next buttons. The transport bar can hide the source
 * subtitles (remembered per device); the translation and notes toggles still work without them.
 * Its quiz button shares the lines just watched with a chat app such as Claude ([QuizShare]).
 * Controls keep clear of the camera cutout.
 * Read-only: no editing on the phone.
 */
@Composable
fun WatchScreen(
  title: Title,
  document: StudyDocument,
  store: LibraryStore,
  settings: Settings,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val timeline = remember(document) { CueTimeline(document.video().cues()) }
  val playback = remember(document) { VideoPlayback(context, title.mediaUri!!, timeline) }
  var autoPause by remember { mutableStateOf(settings.autoPause) }
  var speed by remember { mutableStateOf(settings.playbackSpeed) }
  var subtitles by remember { mutableStateOf(settings.showSubtitles) }
  var quizzing by remember { mutableStateOf(false) }
  var help by remember { mutableStateOf<Help?>(null) }
  var explored by remember { mutableStateOf<Explored?>(null) }
  // Whether the learner paused, which shows the transport bar; auto-pause and phrase taps do not.
  var paused by remember { mutableStateOf(true) }
  val toggle = {
    if (playback.playing) paused = true
    playback.toggle()
  }

  FullScreenLandscape(keepScreenOn = playback.playing)
  BackHandler(onBack = onBack)

  DisposableEffect(playback) {
    store.progress(title.path)?.positionSeconds?.let(playback::seek)
    onDispose {
      store.savePosition(title.path, playback.position, playback.fraction)
      playback.release()
    }
  }
  LaunchedEffect(autoPause) {
    playback.autoPause = autoPause
    settings.autoPause = autoPause
  }
  LaunchedEffect(speed) {
    playback.setSpeed(speed)
    settings.playbackSpeed = speed
  }
  LaunchedEffect(playback) {
    var saved = 0L
    while (true) {
      withFrameMillis { now ->
        playback.tick()
        if (playback.playing && now - saved > 5000) {
          saved = now
          store.savePosition(title.path, playback.position, playback.fraction)
        }
      }
    }
  }
  LaunchedEffect(playback.playing) {
    if (playback.playing) {
      help = null
      explored = null
      paused = false
    } else store.savePosition(title.path, playback.position, playback.fraction)
  }
  LifecycleStartEffect(playback) { onStopOrDispose { playback.pause() } }

  val time = playback.time
  val showing = timeline.showing(time)
  val current = timeline.cues.getOrNull(maxOf(0, timeline.current(time)))
  val sentenceNotes = current?.line()?.notes().orEmpty()
  val exploredCue = showing.firstOrNull { it.line().id() == explored?.cueId }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    ContentFrame(player = playback.player, modifier = Modifier.fillMaxSize())

    // Taps toggle playback (or dismiss an explanation); horizontal swipes step between lines.
    Box(
      Modifier.fillMaxSize()
        .pointerInput(playback) {
          detectTapGestures(
            onTap = {
              if (explored != null || help != null) {
                explored = null
                help = null
              } else toggle()
            }
          )
        }
        .pointerInput(playback) {
          var distance = 0f
          detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = {
              if (abs(distance) > 60.dp.toPx()) playback.step(if (distance < 0) 1 else -1)
            },
          ) { change, amount ->
            change.consume()
            distance += amount
          }
        }
    )

    Column(
      Modifier.align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 84.dp, end = 84.dp, bottom = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val exploredAlignment = exploredCue?.let { readyAlignment(it.line(), document) }
      if (exploredAlignment != null) {
        val phrase = explored!!.phrase
        Panel {
          exploredAlignment.phraseNotes(phrase).forEach { LearnerNote(it, panelStyle) }
        }
        CompositionLocalProvider(LocalContentColor provides Color(0xFF9AA6B2)) {
          TranslationContext(
            exploredAlignment,
            phrase,
            subtitleStyle,
            Modifier.background(Color(0xCC000000), RoundedCornerShape(6.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp),
            centred = true,
            separator = ChunkSeparator.of(document.translationLanguage()),
            onTap = { index ->
              playback.pause()
              explored = Explored(exploredCue.line().id(), index)
            },
          )
        }
      } else if (help == Help.NOTES) {
        Panel {
          Text("Sentence notes", style = panelStyle.copy(color = Color.White, fontSize = 13.sp))
          if (sentenceNotes.isEmpty()) Text("No sentence notes for this line.", style = panelStyle)
          sentenceNotes.forEach { LearnerNote("• $it", panelStyle) }
        }
      } else if (help == Help.TRANSLATION) {
        for (cue in showing) {
          val line = cue.line()
          val alignment = readyAlignment(line, document)
          val translationModifier = Modifier.background(Color(0xDD101216), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
          if (alignment != null) TranslationContext(
            alignment, null, subtitleStyle, translationModifier,
            centred = true, separator = ChunkSeparator.of(document.translationLanguage()),
            onTap = { index -> playback.pause(); explored = Explored(line.id(), index) },
          ) else if (!line.translation().isNullOrBlank()) Text(
            line.translation().orEmpty(), style = subtitleStyle,
            textAlign = TextAlign.Center, modifier = translationModifier,
          )
        }
      }
      if (subtitles) for (cue in showing) {
        val line = cue.line()
        val alignment = readyAlignment(line, document)
        if (alignment != null)
          PhraseLine(
            alignment,
            active = explored?.takeIf { it.cueId == line.id() }?.phrase,
            style = subtitleStyle,
            look = SubtitleLook,
            centred = true,
            onTap = { index ->
              playback.pause()
              explored =
                Explored(line.id(), index).takeUnless { it == explored }
            },
          )
        else
          Text(
            line.source(),
            style = subtitleStyle,
            textAlign = TextAlign.Center,
            modifier =
              Modifier.background(Color(0xDD000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
          )
      }
    }

    Column(
      Modifier.align(Alignment.CenterEnd)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .padding(end = railMargin)
        .background(Color(0x99101419), RoundedCornerShape(28.dp))
        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(28.dp))
        .padding(railPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      RailButton(Icons.Filled.SkipPrevious, "Previous line") {
        explored = null
        playback.step(-1)
      }
      RailButton(Icons.Filled.Replay, "Replay line") {
        explored = null
        playback.replay(maxOf(0, timeline.current(time)))
      }
      RailButton(Icons.Filled.SkipNext, "Next line") {
        explored = null
        playback.step(1)
      }
      Box(Modifier.padding(vertical = 4.dp).size(width = 22.dp, height = 1.dp).background(Color(0x40FFFFFF)))
      RailButton(Icons.Filled.PauseCircleOutline, "Auto-pause", selected = autoPause) {
        autoPause = !autoPause
      }
      RailButton(Icons.Filled.Translate, "Line translation", selected = help == Help.TRANSLATION) {
        explored = null
        help = if (help == Help.TRANSLATION) null else Help.TRANSLATION
      }
      RailButton(
        Icons.AutoMirrored.Filled.Notes,
        "Sentence notes",
        selected = help == Help.NOTES,
        dimmed = sentenceNotes.isEmpty(),
        badge = sentenceNotes.isNotEmpty(),
      ) {
        explored = null
        help = if (help == Help.NOTES) null else Help.NOTES
      }
    }

    if (!playback.playing && (paused || playback.error != null))
      TransportBar(
        title = title.name,
        time = time,
        duration = playback.durationSeconds,
        speed = speed,
        subtitles = subtitles,
        status = playback.error,
        playing = playback.playing,
        onBack = onBack,
        onToggle = toggle,
        onSeek = playback::seek,
        onSpeed = { speed = it },
        onQuiz = { quizzing = true },
        onSubtitles = {
          subtitles = !subtitles
          settings.showSubtitles = subtitles
          explored = null
        },
        modifier = Modifier.align(Alignment.TopCenter),
      )

    if (quizzing)
      QuizDialog(
        document,
        initialType = settings.quizType,
        onDismiss = { quizzing = false },
        onShare = { type, _ ->
          settings.quizType = type
          quizzing = false
          QuizShare.share(context, document, timeline.cues, timeline.current(time), type)
        },
        scopeNote = "Uses the last ${QuizPack.LINES} lines you watched.",
      )
  }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
  Column(
    Modifier.widthIn(max = 460.dp)
      .heightIn(max = 170.dp)
      .background(panelBackground, RoundedCornerShape(8.dp))
      .border(1.dp, Color(0xFF454A53), RoundedCornerShape(8.dp))
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    content()
  }
}

/** A round button in the right-hand rail pill; a selected toggle uses the warm brand accent. */
@Composable
private fun RailButton(
  icon: ImageVector,
  label: String,
  selected: Boolean = false,
  dimmed: Boolean = false,
  badge: Boolean = false,
  onClick: () -> Unit,
) {
  Box {
    IconButton(
      onClick = onClick,
      modifier =
        Modifier.size(railButton)
          .alpha(if (dimmed && !selected) 0.4f else 1f)
          .background(if (selected) Color(0x994B302C) else Color.Transparent, CircleShape),
    ) {
      Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.9f))
    }
    if (badge)
      Box(
        Modifier.align(Alignment.TopEnd)
          .padding(top = 4.dp, end = 4.dp)
          .size(9.dp)
          .background(Color(0xFFE8B97E), CircleShape)
      )
  }
}

@Composable
private fun TransportBar(
  title: String,
  time: Double,
  duration: Double,
  speed: Float,
  subtitles: Boolean,
  status: String?,
  playing: Boolean,
  onBack: () -> Unit,
  onToggle: () -> Unit,
  onSeek: (Double) -> Unit,
  onSpeed: (Float) -> Unit,
  onSubtitles: () -> Unit,
  onQuiz: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var speedMenu by remember { mutableStateOf(false) }
  Column(
    modifier.fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x66000000), Color.Transparent)))
      .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
      // Stops short of the rail pill, so the two sit side by side instead of overlapping.
      .padding(start = 8.dp, end = railMargin + railButton + railPadding * 2 + 8.dp, top = 2.dp, bottom = 14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
      }
      Text(
        status ?: title,
        color = if (status != null) Color(0xFFFFB4A9) else Color.White,
        fontSize = 15.sp,
        maxLines = 1,
        modifier = Modifier.weight(1f),
      )
      Text(
        "${clock(time)} / ${clock(duration)}",
        color = Color(0xFFC8D4E0),
        fontSize = 13.sp,
      )
      QuizBadge(onQuiz)
      IconButton(onClick = onSubtitles) {
        Icon(
          if (subtitles) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
          contentDescription = if (subtitles) "Hide subtitles" else "Show subtitles",
          tint = Color.White,
        )
      }
      Box {
        IconButton(onClick = { speedMenu = true }) {
          Icon(Icons.Filled.Speed, "Playback speed", tint = Color.White)
        }
        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
          for (option in speeds)
            DropdownMenuItem(
              text = { Text(if (option == speed) "✓ ${option}×" else "   ${option}×") },
              onClick = {
                onSpeed(option)
                speedMenu = false
              },
            )
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (duration > 0)
        ThinSlider(
          time.toFloat(),
          0f..duration.toFloat(),
          onValueChange = { onSeek(it.toDouble()) },
          modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
      else Box(Modifier.weight(1f))
      IconButton(onClick = onToggle) {
        Icon(
          if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playing) "Pause" else "Play",
          tint = Color.White,
        )
      }
    }
  }
}

private fun clock(seconds: Double): String {
  val total = seconds.toInt().coerceAtLeast(0)
  val h = total / 3600
  val m = total % 3600 / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Locks landscape and hides the system bars while the player is on screen. */
@Composable
private fun FullScreenLandscape(keepScreenOn: Boolean) {
  val view = LocalView.current
  DisposableEffect(view) {
    val activity = view.context as Activity
    val controller = WindowCompat.getInsetsController(activity.window, view)
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose {
      controller.show(WindowInsetsCompat.Type.systemBars())
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      view.keepScreenOn = false
    }
  }
  LaunchedEffect(keepScreenOn) { view.keepScreenOn = keepScreenOn }
}

