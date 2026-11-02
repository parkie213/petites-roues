package com.petitesroues.android.comic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitesroues.android.study.LearnerNote
import com.petitesroues.android.study.PhraseParagraphLine
import com.petitesroues.android.study.TranslationContext
import com.petitesroues.android.study.phraseNotes
import com.petitesroues.android.study.readyAlignment
import com.petitesroues.model.document.StudyCue

private val sourceStyle = TextStyle(color = Color(0xFFE8EDF2), fontSize = 18.sp, lineHeight = 26.sp)
private val translationStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
private val noteStyle = TextStyle(color = Color(0xFFC8D4E0), fontSize = 14.sp, lineHeight = 21.sp)

/**
 * A bubble's text as a line of phrases with its whole translation and sentence notes beneath, as
 * the browser's bubble card. Tapping a phrase, or a translation chunk, highlights its part of the
 * translation and shows its explanation in place of the sentence notes; tapping again clears it.
 * The ask and listen buttons sit at the bottom of the card. The ask button hands [onAsk] the phrase being explored, if any, to ask a chat app about it.
 */
@Composable
fun BubbleCard(cue: StudyCue, readAloud: ReadAloud, onAsk: (Int?) -> Unit, modifier: Modifier = Modifier) {
  val alignment = readyAlignment(cue)
  var pinned by remember(cue.id()) { mutableStateOf<Int?>(null) }
  val explore = { index: Int -> pinned = if (pinned == index) null else index }
  Column(
    modifier.verticalScroll(rememberScrollState()).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (alignment != null) PhraseParagraphLine(alignment, pinned, sourceStyle, explore)
    else Text(cue.source(), style = sourceStyle)
    if (alignment != null)
      TranslationContext(alignment, pinned, translationStyle, onTap = explore)
    else
      Text(
        cue.translation().orEmpty().ifBlank { "No translation for this bubble yet." },
        style = translationStyle,
      )
    val phrase = pinned
    if (alignment != null && phrase != null) {
      HorizontalDivider(color = Color(0x22FFFFFF))
      alignment.phraseNotes(phrase).forEach { LearnerNote(it, noteStyle) }
    } else if (cue.notes().isNotEmpty()) {
      HorizontalDivider(color = Color(0x22FFFFFF))
      cue.notes().forEach { LearnerNote("• $it", noteStyle) }
    } else if (alignment != null) {
      HorizontalDivider(color = Color(0x22FFFFFF))
      Text("Tap a phrase for its explanation.", style = noteStyle.copy(color = Color(0xFF8592A0)))
    }
    // Ask and Listen sit in a short row under the notes, leaving the source line the card's width.
    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      IconButton(onClick = { onAsk(pinned) }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Ask about this bubble", modifier = Modifier.size(20.dp))
      }
      IconButton(onClick = { readAloud.toggle(cue.source()) }, modifier = Modifier.size(36.dp)) {
        Icon(
          if (readAloud.speaking == cue.source()) Icons.Filled.Stop
          else Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription = "Listen",
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
