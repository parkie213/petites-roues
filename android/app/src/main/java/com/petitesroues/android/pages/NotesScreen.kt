package com.petitesroues.android.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow.Companion.Clip
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

/**
 * The study notes: the list of [notes] (null while they are read), or one note rendered from its
 * Markdown. Back closes the note, then leaves through [onBack]. The repository name follows each
 * title when the notes come from several content folders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(notes: List<MarkdownPage>?, onBack: () -> Unit) {
  var openKey by rememberSaveable { mutableStateOf<String?>(null) }
  val open = notes?.firstOrNull { it.key == openKey }
  BackHandler { if (open != null) openKey = null else onBack() }
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = { if (open != null) openKey = null else onBack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, if (open != null) "Notes" else "Library")
          }
        },
        title = { Text(open?.title ?: "Notes", maxLines = 1, overflow = TextOverflow.Ellipsis) },
      )
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when {
        notes == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        open != null ->
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            NoteMarkdown(open.text)
          }
        notes.isEmpty() ->
          Text(
            "No notes yet. Add Markdown files to a notes folder at the top of a content folder.",
            modifier = Modifier.padding(16.dp),
          )
        else -> {
          val several = notes.map { it.repository }.distinct().size > 1
          LazyColumn {
            items(notes.size) { i ->
              val note = notes[i]
              ListItem(
                headlineContent = { Text(note.title) },
                supportingContent = if (several) ({ Text(note.repository) }) else null,
                leadingContent = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { openKey = note.key },
              )
            }
          }
        }
      }
    }
  }
}

/**
 * A note's Markdown sized for reading on a phone: headings scaled down from the library's display
 * sizes, and table cells that wrap (the library's default cuts them to one line), with narrower
 * columns so most tables fit and wider ones scroll sideways. The page's own `# ` heading is left
 * out because the top bar shows it.
 */
@Composable
private fun NoteMarkdown(text: String) {
  val lines = text.lines().dropWhile { it.isBlank() }
  val body = (if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n")
  val type = MaterialTheme.typography
  Markdown(
    content = body,
    modifier = Modifier.fillMaxWidth(),
    typography =
      markdownTypography(
        h1 = type.headlineSmall,
        h2 = type.titleLarge,
        h3 = type.titleMedium,
        h4 = type.titleSmall,
        h5 = type.titleSmall,
        h6 = type.titleSmall,
        table = type.bodyMedium,
      ),
    dimens = markdownDimens(tableCellWidth = 112.dp, tableCellPadding = 8.dp),
    padding = markdownPadding(block = 6.dp),
    components =
      markdownComponents(
        table = {
          MarkdownTable(
            it.content,
            it.node,
            style = it.typography.table,
            headerBlock = { content, header, width, style ->
              MarkdownTableHeader(content, header, width, style, maxLines = Int.MAX_VALUE, overflow = Clip)
            },
            rowBlock = { content, row, width, style ->
              MarkdownTableRow(content, row, width, style, maxLines = Int.MAX_VALUE, overflow = Clip)
            },
          )
        }
      ),
  )
}
