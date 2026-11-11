package app.petitesroues.android.comic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.petitesroues.model.document.ComicBox
import app.petitesroues.model.document.ComicChapter
import app.petitesroues.model.document.Line

/** Clear drawing and Reset zoom, each shown only while there is something to clear or reset. */
@Composable
internal fun SketchActions(sketch: Sketch, zoomed: Boolean, onResetZoom: () -> Unit) {
  if (sketch.strokes.isNotEmpty())
    IconButton(onClick = sketch::clear) {
      Icon(Icons.Filled.DeleteSweep, "Clear drawing", tint = Color.White)
    }
  if (zoomed)
    IconButton(onClick = onResetZoom) {
      Icon(Icons.Filled.FitScreen, "Reset zoom", tint = Color.White)
    }
}

/** Asks what the learner wants to know about a bubble, before sharing it to a chat app. */
@Composable
internal fun AskDialog(phrase: String?, onDismiss: () -> Unit, onAsk: (String) -> Unit) {
  var question by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ask about this bubble") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        phrase?.let { Text("About “$it”", fontSize = 14.sp, color = Color(0xFF9AA6B2)) }
        OutlinedTextField(
          question,
          onValueChange = { question = it },
          placeholder = { Text("What do you want to ask?") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onAsk(question) }) { Text("Share") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** A question being written about [line], held by [box], exploring source [phrase] if any. */
internal data class Question(val box: ComicBox, val line: Line, val phrase: Int?)

/**
 * The page bar's position label: the page, prefixed by its chapter when the volume lists chapters.
 * Tapping it then opens the chapter list, and choosing one jumps to that chapter's first page.
 */
@Composable
internal fun ChapterLabel(
  page: String,
  chapters: List<ComicChapter>,
  current: Int,
  onChapter: (Int) -> Unit,
  pageOf: (ComicChapter) -> Int,
) {
  val color = Color(0xFFC8D4E0)
  if (chapters.isEmpty()) {
    Text(page, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    return
  }
  var open by remember { mutableStateOf(false) }
  Box(Modifier.padding(start = 4.dp)) {
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
      val prefix = chapters.getOrNull(current)?.let { Chapters.short(it) + " · " }.orEmpty()
      Text(prefix + page, color = color, fontSize = 13.sp)
      Icon(Icons.Filled.ArrowDropDown, "Chapters", tint = color)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      chapters.forEachIndexed { i, chapter ->
        DropdownMenuItem(
          text = {
            Text(Chapters.name(chapter), fontWeight = if (i == current) FontWeight.Bold else null)
          },
          onClick = {
            open = false
            pageOf(chapter).takeIf { it >= 0 }?.let(onChapter)
          },
        )
      }
    }
  }
}
