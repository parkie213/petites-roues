package app.petitesroues.android.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val railButton = 42.dp
private val railPadding = 5.dp
internal val railMargin = 12.dp

/** How far subtitles and the seek bar keep from the side the rail is on. */
internal val railClearance = 140.dp

/** One column of persistent shortcuts; both columns share the minimise button beneath them. */
@Composable
internal fun RailPill(content: @Composable () -> Unit) {
  Column(
    Modifier.background(Color(0xCC101419), RoundedCornerShape(28.dp))
      .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(28.dp))
      .padding(railPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    content()
  }
}

/** A round button in the right-hand rail pill; a selected toggle uses the warm brand accent. */
@Composable
internal fun RailButton(
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
