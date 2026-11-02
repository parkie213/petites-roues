package com.petitesroues.android.quiz

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
/**
 * The quiz button of the player and comic reader: a labelled pill rather than a bare glyph, so it
 * reads as "quiz me" at a glance and stays distinct from the icon-only controls beside it.
 */
@Composable
fun QuizBadge(onClick: () -> Unit, description: String = "Quiz me on these lines in another app") {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = Color(0x33FFFFFF),
    contentColor = Color.White,
    modifier = Modifier.padding(horizontal = 6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
      Icon(Icons.Outlined.Lightbulb, description, Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("Quiz", fontSize = 13.sp)
    }
  }
}
