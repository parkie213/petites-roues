package app.petitesroues.android.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow.Companion.Clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

// The desktop's study page colours (web/src/library/Notes.css and QuizActions.css).
private val Selected = Color(0xFFF6E4DC)
private val SelectedBar = Color(0xFFD94B3A)
private val Card = Color(0xFFFFFCF7)
private val CardBorder = Color(0xFFDCDAD3)
private val TableHeader = Color(0xFFEEEBE4)
private val CodeBackground = Color(0xFFEEE9E1)
private val CodeText = Color(0xFF9E3F31)
private val Chat = Color(0xFF3568A8)

/** The library's three tabs, as on the desktop. [folder] is where a study section's pages live. */
enum class Section(val label: String, val folder: String?, val noun: String) {
  LIBRARY("Library", null, "title"),
  NOTES("Notes", MarkdownPage.NOTES, "note"),
  QUIZZES("Quiz me", MarkdownPage.QUIZZES, "quiz"),
}

/** The Library / Notes / Quiz me tabs: the current one in coral with an underline. */
@Composable
fun StudyTabs(section: Section, onSection: (Section) -> Unit) {
  val underline = MaterialTheme.colorScheme.primary
  Column {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
      for (tab in Section.entries) {
        val active = tab == section
        Text(
          tab.label,
          style =
            MaterialTheme.typography.titleSmall.copy(
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
            ),
          color = if (active) underline else MaterialTheme.colorScheme.onSurface,
          modifier =
            Modifier.clickable { onSection(tab) }
              .padding(horizontal = 12.dp)
              .drawBehind {
                if (active) {
                  val height = 2.dp.toPx()
                  drawRect(underline, Offset(0f, size.height - height), Size(size.width, height))
                }
              }
              .padding(vertical = 12.dp),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/**
 * A study section's pages, laid out like the desktop's: a heading with the count, a search box and
 * the topics, the last one opened marked with a coral bar; then one page read on its own with a
 * breadcrumb back. A quiz adds the "Continue in a chat" card above its text. [pages] is null while
 * they are read. [remembered] is the page to mark before one is opened here. Back closes the page,
 * then leaves through [onBack].
 */
@Composable
fun StudyPages(
  section: Section,
  pages: List<MarkdownPage>?,
  remembered: String?,
  onBack: () -> Unit,
  onOpened: (MarkdownPage) -> Unit,
  onChat: (MarkdownPage, app: String?) -> Unit,
  onCopy: (MarkdownPage) -> Unit,
) {
  var openKey by rememberSaveable(section) { mutableStateOf<String?>(null) }
  var marked by rememberSaveable(section) { mutableStateOf(remembered) }
  var query by rememberSaveable(section) { mutableStateOf("") }
  val open = pages?.firstOrNull { it.key == openKey }
  BackHandler { if (open != null) openKey = null else onBack() }
  val quiz = section == Section.QUIZZES
  when {
    pages == null ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    open != null -> Reading(section, open, quiz, onClose = { openKey = null }, onChat, onCopy)
    else -> {
      val several = pages.map { it.repository }.distinct().size > 1
      val shown = pages.filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
      }
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
      ) {
        item {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              section.label,
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            Text(
              "${pages.size} ${if (pages.size == 1) "topic" else "topics"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        item {
          OutlinedTextField(
            query,
            { query = it },
            placeholder = { Text("Find a ${section.noun}") },
            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Card,
                focusedContainerColor = Card,
                unfocusedBorderColor = CardBorder,
              ),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
          )
        }
        if (pages.isEmpty())
          item {
            Text(
              "No ${section.noun}s yet. Add Markdown files to a ${section.folder} folder at the top of a content folder, or get them from your desktop.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
        items(shown, key = { it.key }) { page ->
          Topic(page, page.key == marked, several) {
            marked = page.key
            openKey = page.key
            onOpened(page)
          }
        }
      }
    }
  }
}

@Composable
private fun Topic(page: MarkdownPage, selected: Boolean, several: Boolean, onClick: () -> Unit) {
  Column(
    Modifier.fillMaxWidth()
      .background(
        if (selected) Selected else Color.Transparent,
        RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
      )
      .drawBehind { if (selected) drawRect(SelectedBar, size = Size(3.dp.toPx(), size.height)) }
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    Text(page.title, style = MaterialTheme.typography.bodyLarge)
    if (several)
      Text(
        page.repository,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }
}

@Composable
private fun Reading(
  section: Section,
  page: MarkdownPage,
  quiz: Boolean,
  onClose: () -> Unit,
  onChat: (MarkdownPage, app: String?) -> Unit,
  onCopy: (MarkdownPage) -> Unit,
) {
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    Modifier.fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    Row(
      Modifier.clickable(onClick = onClose).padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        "Back to ${section.label}",
        Modifier.size(16.dp),
        tint = dim,
      )
      Text(
        "  ${section.label} / ${page.repository}",
        style = MaterialTheme.typography.bodySmall,
        color = dim,
      )
    }
    Text(
      page.title,
      style =
        MaterialTheme.typography.headlineSmall.copy(
          fontSize = 26.sp,
          lineHeight = 32.sp,
          fontWeight = FontWeight.SemiBold,
        ),
      modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
    )
    if (quiz) {
      Text(
        "Practise with a chat, one question at a time.",
        style = MaterialTheme.typography.bodySmall,
        color = dim,
      )
      ChatCard(onChat = { onChat(page, it) }, onCopy = { onCopy(page) })
    }
    StudyMarkdown(page.text)
  }
}

/**
 * The quiz's "Continue in a chat" card: open ChatGPT or Claude with the quiz attached, or copy or
 * share it.
 */
@Composable
private fun ChatCard(onChat: (app: String?) -> Unit, onCopy: () -> Unit) {
  val shape = RoundedCornerShape(8.dp)
  Column(
    Modifier.fillMaxWidth()
      .padding(vertical = 16.dp)
      .background(Card, shape)
      .border(1.dp, CardBorder, shape)
      .padding(16.dp)
  ) {
    Text(
      "Continue in a chat",
      style =
        MaterialTheme.typography.titleSmall.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 15.sp,
        ),
    )
    Row(
      Modifier.fillMaxWidth().padding(top = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val buttonShape = RoundedCornerShape(6.dp)
      Button(
        onClick = { onChat(CHATGPT) },
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Chat, contentColor = Color.White),
        modifier = Modifier.weight(1f),
      ) {
        Text("ChatGPT")
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(start = 6.dp).size(16.dp))
      }
      OutlinedButton(
        onClick = { onChat(CLAUDE) },
        shape = buttonShape,
        border = BorderStroke(1.dp, Chat),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Chat),
        modifier = Modifier.weight(1f),
      ) {
        Text("Claude")
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(start = 6.dp).size(16.dp))
      }
    }
    Text(
      "Opens the app with the quiz attached. Send it to begin.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
    )
    HorizontalDivider(color = CardBorder)
    Row(Modifier.padding(top = 4.dp)) {
      val small = MaterialTheme.typography.bodySmall
      TextButton(
        onClick = onCopy,
        colors =
          ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
      ) {
        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
        Text("Copy prompt", style = small, modifier = Modifier.padding(start = 6.dp))
      }
      TextButton(
        onClick = { onChat(null) },
        colors =
          ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
      ) {
        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
        Text("Share…", style = small, modifier = Modifier.padding(start = 6.dp))
      }
    }
  }
}

/** The ChatGPT and Claude apps' package names. */
const val CHATGPT = "com.openai.chatgpt"
const val CLAUDE = "com.anthropic.claude"

/**
 * A page's Markdown styled like the desktop's study pages and sized for a phone: roomy body text,
 * code in coral on a warm chip, tables with a shaded header and cells that wrap (the renderer's
 * default cuts them to one line), narrower columns so most tables fit and wider ones scroll
 * sideways. The page's own `# ` heading is left out because the page shows it above.
 */
@Composable
private fun StudyMarkdown(text: String) {
  val lines = text.lines().dropWhile { it.isBlank() }
  val body =
    (if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n")
  val ink = MaterialTheme.colorScheme.onSurface
  val prose =
    MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 27.sp, color = ink)
  val heading = prose.copy(fontWeight = FontWeight.SemiBold)
  val code =
    prose.copy(
      fontFamily = FontFamily.Monospace,
      fontSize = 14.sp,
      lineHeight = 22.sp,
      color = CodeText,
    )
  Markdown(
    content = body,
    modifier = Modifier.fillMaxWidth(),
    colors =
      markdownColor(
        text = ink,
        codeBackground = CodeBackground,
        inlineCodeBackground = CodeBackground,
        dividerColor = CardBorder,
        tableBackground = Card,
      ),
    typography =
      markdownTypography(
        h1 = heading.copy(fontSize = 23.sp, lineHeight = 30.sp),
        h2 = heading.copy(fontSize = 21.sp, lineHeight = 28.sp),
        h3 = heading.copy(fontSize = 18.sp, lineHeight = 25.sp),
        h4 = heading,
        h5 = heading,
        h6 = heading,
        text = prose,
        paragraph = prose,
        ordered = prose,
        bullet = prose,
        list = prose,
        quote = prose.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        code = code,
        inlineCode = code.copy(fontSize = 15.sp),
        table = MaterialTheme.typography.bodyMedium.copy(color = ink),
      ),
    dimens = markdownDimens(tableCellWidth = 112.dp, tableCellPadding = 8.dp),
    padding = markdownPadding(block = 8.dp),
    components =
      markdownComponents(
        table = {
          Box(
            Modifier.padding(vertical = 4.dp).border(1.dp, CardBorder, RoundedCornerShape(5.dp))
          ) {
            MarkdownTable(
              it.content,
              it.node,
              style = it.typography.table,
              headerBlock = { content, header, width, style ->
                Box(Modifier.width(width).background(TableHeader)) {
                  MarkdownTableHeader(
                    content,
                    header,
                    width,
                    style.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = Int.MAX_VALUE,
                    overflow = Clip,
                  )
                }
              },
              rowBlock = { content, row, width, style ->
                MarkdownTableRow(
                  content,
                  row,
                  width,
                  style,
                  maxLines = Int.MAX_VALUE,
                  overflow = Clip,
                )
              },
            )
          }
        }
      ),
  )
}
