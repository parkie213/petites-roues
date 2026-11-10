package com.petitesroues.android.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitesroues.android.ThinSlider
import com.petitesroues.android.quiz.QuizBadge

private val speeds = listOf(0.5f, 0.6f, 0.8f, 1f, 1.25f, 1.5f)

/**
 * The player's top bar and seek bar, shown while the controls are: back, the title (or why the
 * video can't play), quiz, subtitles, speed and which side the rail is on, then the position.
 */
@Composable
internal fun TransportBar(
  title: String,
  time: Double,
  duration: Double,
  speed: Float,
  subtitles: Boolean,
  status: String?,
  onBack: () -> Unit,
  onSeek: (Double) -> Unit,
  onSpeed: (Float) -> Unit,
  onSubtitles: () -> Unit,
  onQuiz: () -> Unit,
  controlsLeft: Boolean,
  onSwapSides: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var speedMenu by remember { mutableStateOf(false) }
  Column(
    modifier
      .background(
        Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x66000000), Color.Transparent))
      )
      .windowInsetsPadding(
        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
      )
      .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
  ) {
    // The top row stays put when the rail changes sides, so the swap button doesn't move away.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(end = railClearance - 8.dp),
    ) {
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
          for (option in speeds) DropdownMenuItem(
            text = { Text(if (option == speed) "✓ ${option}×" else "   ${option}×") },
            onClick = {
              onSpeed(option)
              speedMenu = false
            },
          )
        }
      }
      IconButton(onClick = onSwapSides) {
        Icon(
          Icons.Filled.SwapHoriz,
          if (controlsLeft) "Move controls to the right" else "Move controls to the left",
          tint = Color.White,
        )
      }
    }
    Spacer(Modifier.weight(1f))
    // The seek bar and time stop short of the rail, so the two sit side by side.
    Column(
      Modifier.padding(
        start = if (controlsLeft) railClearance - 8.dp else 0.dp,
        end = if (controlsLeft) 0.dp else railClearance - 8.dp,
      )
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (duration > 0)
          ThinSlider(
            time.toFloat(),
            0f..duration.toFloat(),
            onValueChange = { onSeek(it.toDouble()) },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
          )
        else Box(Modifier.weight(1f))
      }
      Text(
        "${clock(time)} / ${clock(duration)}",
        color = Color(0xFFC8D4E0),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp),
      )
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
