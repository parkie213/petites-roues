package app.petitesroues.android.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import app.petitesroues.android.quiz.QuizDialog
import app.petitesroues.android.quiz.QuizPack
import app.petitesroues.android.quiz.QuizShare
import app.petitesroues.android.study.LearnerNote
import app.petitesroues.android.study.PhraseLine
import app.petitesroues.android.study.SubtitleLook
import app.petitesroues.android.study.TranslationContext
import app.petitesroues.android.study.phraseNotes
import app.petitesroues.android.study.readyAlignment
import app.petitesroues.model.ChunkSeparator
import kotlin.math.abs
import kotlinx.coroutines.delay

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

/**
 * The full-screen landscape player: the video with tappable phrase subtitles, a pill of line
 * shortcuts and study toggles on the right. Tapping the video reveals transport controls without
 * changing playback; a central button and a thumb button in the second rail column play/pause. Both
 * rail columns collapse together. Phrase cycling pauses on each explanation in source order.
 * Horizontal swipes step between lines. Translation appears above the source subtitles. The
 * transport bar can hide source subtitles (remembered per device). Its quiz button shares the lines
 * just watched with a chat app such as Claude ([QuizShare]). Controls keep clear of the camera
 * cutout. Read-only: no editing on the phone.
 */
// ContentFrame, the Compose video surface, is Media3 API still marked unstable.
@OptIn(UnstableApi::class)
@Composable
fun WatchScreen(model: PlayerViewModel, onBack: () -> Unit) {
  val context = LocalContext.current
  val title = model.title
  val document = model.document
  val timeline = model.timeline
  val playback = model.playback
  val choices by model.state.collectAsStateWithLifecycle()
  val controlsLeft = choices.controlsLeft
  var quizzing by remember { mutableStateOf(false) }
  var help by remember { mutableStateOf<Help?>(null) }
  var explored by remember { mutableStateOf<Explored?>(null) }
  var controlsVisible by remember { mutableStateOf(true) }
  var railExpanded by remember { mutableStateOf(true) }
  val toggle = { playback.toggle() }

  LaunchedEffect(controlsVisible, playback.playing) {
    if (controlsVisible && playback.playing) {
      delay(3500)
      controlsVisible = false
    }
  }

  FullScreenLandscape(keepScreenOn = playback.playing)
  BackHandler(onBack = onBack)

  LaunchedEffect(playback) { while (true) withFrameMillis { playback.tick() } }
  LaunchedEffect(playback.playing) {
    if (playback.playing) {
      help = null
      explored = null
    }
  }
  LifecycleStartEffect(playback) { onStopOrDispose { playback.pause() } }

  val time = playback.time
  val showing = timeline.showing(time)
  val current = timeline.cues.getOrNull(maxOf(0, timeline.current(time)))
  val sentenceNotes = current?.line()?.notes().orEmpty()
  val exploredCue = showing.firstOrNull { it.line().id() == explored?.cueId }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    ContentFrame(player = playback.player, modifier = Modifier.fillMaxSize())

    // Background taps only reveal or hide controls; horizontal swipes step between lines.
    Box(
      Modifier.fillMaxSize()
        .pointerInput(playback) {
          detectTapGestures(onTap = { controlsVisible = !controlsVisible })
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
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .padding(
          start = if (controlsLeft) railClearance else 24.dp,
          end = if (controlsLeft) 24.dp else railClearance,
          bottom = 12.dp,
        ),
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
          val translationModifier =
            Modifier.background(Color(0xDD101216), RoundedCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp)
          if (alignment != null)
            TranslationContext(
              alignment,
              null,
              subtitleStyle,
              translationModifier,
              centred = true,
              separator = ChunkSeparator.of(document.translationLanguage()),
              onTap = { index ->
                playback.pause()
                explored = Explored(line.id(), index)
              },
            )
          else if (!line.translation().isNullOrBlank())
            Text(
              line.translation().orEmpty(),
              style = subtitleStyle,
              textAlign = TextAlign.Center,
              modifier = translationModifier,
            )
        }
      }
      if (choices.subtitles)
        for (cue in showing) {
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
                explored = Explored(line.id(), index).takeUnless { it == explored }
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
      Modifier.align(if (controlsLeft) Alignment.BottomStart else Alignment.BottomEnd)
        .windowInsetsPadding(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        )
        .padding(
          start = if (controlsLeft) railMargin else 0.dp,
          end = if (controlsLeft) 0.dp else railMargin,
          bottom = 6.dp,
        ),
      horizontalAlignment = if (controlsLeft) Alignment.Start else Alignment.End,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (railExpanded)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          val playPill: @Composable () -> Unit = {
            RailPill {
              RailButton(
                if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playback.playing) "Pause" else "Resume",
                onClick = toggle,
              )
              val phrases = showing.flatMap { cue ->
                readyAlignment(cue.line(), document)?.source().orEmpty().indices.map {
                  Explored(cue.line().id(), it)
                }
              }
              RailButton(
                Icons.Filled.FindInPage,
                "Next phrase explanation",
                dimmed = phrases.isEmpty(),
              ) {
                if (phrases.isNotEmpty()) {
                  playback.pause()
                  help = null
                  controlsVisible = false
                  explored = phrases[(phrases.indexOf(explored) + 1) % phrases.size]
                }
              }
            }
          }
          val linePill: @Composable () -> Unit = {
            RailPill {
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
              Box(
                Modifier.padding(vertical = 4.dp)
                  .size(width = 22.dp, height = 1.dp)
                  .background(Color(0x40FFFFFF))
              )
              RailButton(
                Icons.Filled.PauseCircleOutline,
                "Auto-pause",
                selected = choices.autoPause,
              ) {
                model.setAutoPause(!choices.autoPause)
              }
              RailButton(
                Icons.Filled.Translate,
                "Line translation",
                selected = help == Help.TRANSLATION,
              ) {
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
          }
          if (controlsLeft) {
            linePill()
            playPill()
          } else {
            playPill()
            linePill()
          }
        }
      Surface(color = Color(0xCC101419), shape = CircleShape) {
        RailButton(
          if (railExpanded) Icons.Filled.ExpandMore else Icons.Filled.Tune,
          if (railExpanded) "Minimise player controls" else "Expand player controls",
        ) {
          railExpanded = !railExpanded
        }
      }
    }

    if (controlsVisible || playback.error != null)
      TransportBar(
        title = title.name,
        time = time,
        duration = playback.durationSeconds,
        speed = choices.speed,
        subtitles = choices.subtitles,
        status = playback.error,
        onBack = onBack,
        onSeek = playback::seek,
        onSpeed = model::setSpeed,
        onQuiz = { quizzing = true },
        controlsLeft = controlsLeft,
        onSwapSides = model::swapSides,
        onSubtitles = {
          model.toggleSubtitles()
          explored = null
        },
        modifier = Modifier.fillMaxSize(),
      )

    if (controlsVisible)
      IconButton(
        onClick = toggle,
        modifier =
          Modifier.align(Alignment.Center).size(80.dp).background(Color(0x88000000), CircleShape),
      ) {
        Icon(
          if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (playback.playing) "Pause" else "Play",
          tint = Color.White,
          modifier = Modifier.size(48.dp),
        )
      }

    if (quizzing)
      QuizDialog(
        document,
        initialType = model.quizType,
        onDismiss = { quizzing = false },
        onShare = { type, _ ->
          model.quizShared(type)
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

/** Locks landscape and hides the system bars while the player is on screen. */
@Composable
private fun FullScreenLandscape(keepScreenOn: Boolean) {
  val view = LocalView.current
  DisposableEffect(view) {
    val activity = view.context as Activity
    val controller = WindowCompat.getInsetsController(activity.window, view)
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose {
      controller.show(WindowInsetsCompat.Type.systemBars())
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      view.keepScreenOn = false
    }
  }
  LaunchedEffect(keepScreenOn) { view.keepScreenOn = keepScreenOn }
}
