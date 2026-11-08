package com.petitesroues.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The accent shared by the player and reader progress lines. */
val ProgressColor = Coral

/**
 * A thin seekable progress line with a small round thumb, quieter than Material's slider. Used for
 * the player's position and the comic reader's page. It follows the layout direction, so a
 * right-to-left comic can fill it from the right.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThinSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  onValueChangeFinished: (() -> Unit)? = null,
) {
  val colors =
    SliderDefaults.colors(
      thumbColor = ProgressColor,
      activeTrackColor = ProgressColor,
      inactiveTrackColor = Color(0x4DFFFFFF),
    )
  Slider(
    value = value.coerceIn(range.start, range.endInclusive),
    onValueChange = onValueChange,
    onValueChangeFinished = onValueChangeFinished,
    valueRange = range,
    colors = colors,
    modifier = modifier,
    thumb = {
      Box(
        Modifier.size(14.dp)
          .background(Color.White, CircleShape)
          .padding(2.dp)
          .background(ProgressColor, CircleShape)
      )
    },
    track = { state ->
      SliderDefaults.Track(
        sliderState = state,
        colors = colors,
        thumbTrackGapSize = 0.dp,
        drawStopIndicator = null,
        modifier = Modifier.height(4.dp),
      )
    },
  )
}
