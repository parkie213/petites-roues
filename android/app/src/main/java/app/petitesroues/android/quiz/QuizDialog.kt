package app.petitesroues.android.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.document.VideoCue

/**
 * What a video quiz can cover: its sorted [cues], their title's [length] and the player's state.
 */
class VideoQuiz(
  val cues: List<VideoCue>,
  val length: Double,
  val position: Double,
  val current: Int,
)

/**
 * The Quiz me dialog, as in the browser: the chapter to quiz on (when [chapters] names a comic's
 * chapters, starting at [initialChapter]), or for a [video] the scope of lines (whole title, time
 * range, or the last lines), and the quiz type (starting at [initialType]). The video scope opens
 * on [QuizScope.initial] each time, never on what another title used. Share hands the choice to
 * [onShare], which opens the share sheet; the scope is null for comics.
 */
@Composable
fun QuizDialog(
  document: StudyDocument,
  initialType: String,
  onDismiss: () -> Unit,
  onShare: (typeId: String, chapter: Int, scope: QuizScope?) -> Unit,
  chapters: List<String> = emptyList(),
  initialChapter: Int = 0,
  video: VideoQuiz? = null,
) {
  val choices = remember {
    QuizChoices(document, initialType, chapters.size, initialChapter, video)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Quiz me") },
    text = { QuizForm(document, chapters, choices) },
    confirmButton = {
      Button(
        onClick = { onShare(choices.type, choices.chapter, choices.scope) },
        enabled = video == null || choices.scope != null,
      ) {
        Text("Share…")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * What the dialog has chosen so far: the quiz type, a comic's chapter, and for a [video] the scope
 * choice with its range fields as typed. The video scope starts on [QuizScope.initial].
 */
@Stable
internal class QuizChoices(
  document: StudyDocument,
  initialType: String,
  chapterCount: Int,
  initialChapter: Int,
  val video: VideoQuiz?,
) {
  private val initial = video?.let { QuizScope.initial(document, it.length, it.position) }
  var type by
    mutableStateOf(
      QuizPack.TYPES.firstOrNull { it.id == initialType }?.id ?: QuizPack.TYPES.first().id
    )
  var chapter by mutableIntStateOf(initialChapter.coerceIn(0, (chapterCount - 1).coerceAtLeast(0)))
  var choice by mutableStateOf(initial?.choice ?: QuizScope.Choice.WHOLE)
  var start by mutableStateOf(initial?.start.orEmpty())
  var end by mutableStateOf(initial?.end.orEmpty())

  /** The scope the fields describe; null for a comic, or while a time range cannot be read. */
  val scope: QuizScope?
    get() = video?.let { QuizScope.of(choice, start, end, it.current) }
}

/** The dialog's content: chapter or scope, then the quiz type. Scrolls in a short window. */
@Composable
internal fun QuizForm(document: StudyDocument, chapters: List<String>, choices: QuizChoices) {
  val video = choices.video
  val scope = choices.scope
  val lines = if (video != null && scope != null) QuizScope.select(video.cues, scope).size else 0
  // Scrolls when the dialog is short, as in a landscape video.
  Column(Modifier.verticalScroll(rememberScrollState())) {
    if (chapters.isNotEmpty()) {
      Text("Chapter", style = MaterialTheme.typography.labelLarge)
      var open by remember { mutableStateOf(false) }
      Box {
        OutlinedButton(
          onClick = { open = true },
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
          Text(
            chapters[choices.chapter],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
          chapters.forEachIndexed { i, name ->
            DropdownMenuItem(
              text = { Text(name) },
              onClick = {
                choices.chapter = i
                open = false
              },
            )
          }
        }
      }
    } else if (video != null)
      ScopePicker(
        choices.choice,
        { choices.choice = it },
        choices.start,
        { choices.start = it },
        choices.end,
        { choices.end = it },
        scope != null,
        lines,
      )
    Text(
      "Quiz type",
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(top = 16.dp),
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      for (option in QuizPack.TYPES) Option(choices.type == option.id, option.label(document)) {
        choices.type = option.id
      }
    }
    Text(
      "Share opens the share sheet: pick ChatGPT or Claude to start a chat with the quiz attached.",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 12.dp),
    )
  }
}

/** One radio-style choice in the dialog: a highlighted row that selects on tap. */
@Composable
private fun Option(selected: Boolean, label: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .background(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        RoundedCornerShape(8.dp),
      )
      .border(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        RoundedCornerShape(8.dp),
      )
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, modifier = Modifier.padding(start = 12.dp))
  }
}

/**
 * The video quiz's scope: whole title, a time range (start and end as m:ss or h:mm:ss) or the last
 * lines, then how many lines the scope holds ([valid] is false while a range cannot be read), with
 * a warning above [QuizScope.SUGGESTED_CAP] lines. The user shrinks the scope; nothing is cut.
 */
@Composable
private fun ScopePicker(
  choice: QuizScope.Choice,
  onChoice: (QuizScope.Choice) -> Unit,
  start: String,
  onStart: (String) -> Unit,
  end: String,
  onEnd: (String) -> Unit,
  valid: Boolean,
  lines: Int,
) {
  Text("Quiz scope", style = MaterialTheme.typography.labelLarge)
  Column(
    Modifier.selectableGroup().padding(top = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Option(choice == QuizScope.Choice.WHOLE, "Whole title") { onChoice(QuizScope.Choice.WHOLE) }
    Option(choice == QuizScope.Choice.RANGE, "Time range") { onChoice(QuizScope.Choice.RANGE) }
    Option(choice == QuizScope.Choice.LAST, "Last ${QuizScope.LAST_LINES} lines") {
      onChoice(QuizScope.Choice.LAST)
    }
  }
  if (choice == QuizScope.Choice.RANGE) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((label, value, change) in
        listOf(Triple("Start", start, onStart), Triple("End", end, onEnd))) OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        singleLine = true,
        isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
    }
  }
  Text(
    when {
      !valid -> "Enter times as m:ss or h:mm:ss, with the end after the start."
      lines == 1 -> "1 line in this scope"
      else -> "$lines lines in this scope"
    },
    style = MaterialTheme.typography.bodySmall,
    color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(top = 8.dp),
  )
  if (valid && QuizScope.overCap(lines))
    Text(
      "That is more than ${QuizScope.SUGGESTED_CAP} lines, which may be too much for a chat app. " +
        "Narrow the time range or choose the last ${QuizScope.LAST_LINES} lines.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(top = 4.dp),
    )
}
