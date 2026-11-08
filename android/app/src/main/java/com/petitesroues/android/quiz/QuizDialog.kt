package com.petitesroues.android.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petitesroues.android.pages.MarkdownPage
import com.petitesroues.model.document.StudyDocument

/**
 * The Quiz me dialog, as in the browser: the chapter to quiz on (when [chapters] names a comic's
 * chapters, starting at [initialChapter]) or [scopeNote] otherwise, and the quiz type (starting at
 * [initialType]). Share hands the choice to [onShare], which opens the share sheet.
 */
@Composable
fun QuizDialog(
  document: StudyDocument,
  initialType: String,
  onDismiss: () -> Unit,
  onShare: (typeId: String, chapter: Int) -> Unit,
  chapters: List<String> = emptyList(),
  initialChapter: Int = 0,
  scopeNote: String = "",
) {
  var type by remember { mutableStateOf(QuizPack.TYPES.firstOrNull { it.id == initialType }?.id ?: QuizPack.TYPES.first().id) }
  var chapter by remember { mutableIntStateOf(initialChapter.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quiz me") },
    text = {
      // Scrolls when the dialog is short, as in a landscape video.
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (chapters.isNotEmpty()) {
          Text("Chapter", style = MaterialTheme.typography.labelLarge)
          var open by remember { mutableStateOf(false) }
          Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
              Text(chapters[chapter], maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
              chapters.forEachIndexed { i, name ->
                DropdownMenuItem(
                  text = { Text(name) },
                  onClick = {
                    chapter = i
                    open = false
                  },
                )
              }
            }
          }
        } else Text(scopeNote, style = MaterialTheme.typography.bodyMedium)
        Text("Quiz type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          for (option in QuizPack.TYPES)
            Row(
              Modifier.fillMaxWidth()
                .background(if (type == option.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, if (type == option.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .selectable(selected = type == option.id, onClick = { type = option.id }, role = Role.RadioButton)
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = type == option.id, onClick = null)
              Text(option.label(document), modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(
          "Share opens the share sheet: pick ChatGPT or Claude to start a chat with the quiz attached.",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 12.dp),
        )
      }
    },
    confirmButton = { Button(onClick = { onShare(type, chapter) }) { Text("Share…") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * The library's Quiz me: one of the general [quizzes] from the content folders' `quizzes/`
 * folders, starting at the one keyed [initial]. Share hands the choice to [onShare]. With no
 * quizzes it says how to add some.
 */
@Composable
fun GeneralQuizDialog(
  quizzes: List<MarkdownPage>,
  initial: String?,
  onDismiss: () -> Unit,
  onShare: (MarkdownPage) -> Unit,
) {
  var chosen by remember { mutableStateOf(quizzes.firstOrNull { it.key == initial } ?: quizzes.firstOrNull()) }
  val several = quizzes.map { it.repository }.distinct().size > 1
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quiz me") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (quizzes.isEmpty())
          Text(
            "No quizzes yet. Add Markdown files to a quizzes folder at the top of a content folder.",
            style = MaterialTheme.typography.bodyMedium,
          )
        else {
          Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (quiz in quizzes)
              Row(
                Modifier.fillMaxWidth()
                  .background(if (chosen == quiz) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                  .border(1.dp, if (chosen == quiz) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                  .selectable(selected = chosen == quiz, onClick = { chosen = quiz }, role = Role.RadioButton)
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = chosen == quiz, onClick = null)
                Text(if (several) "${quiz.title} (${quiz.repository})" else quiz.title, modifier = Modifier.padding(start = 12.dp))
              }
          }
          Text(
            "Share opens the share sheet: pick ChatGPT or Claude to start a chat with the quiz attached.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
          )
        }
      }
    },
    confirmButton = { chosen?.let { quiz -> Button(onClick = { onShare(quiz) }) { Text("Share…") } } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
