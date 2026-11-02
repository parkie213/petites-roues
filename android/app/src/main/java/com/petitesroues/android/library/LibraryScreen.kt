package com.petitesroues.android.library

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitesroues.android.ProgressColor
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.MoreHoriz

/** What the library screen shows; the activity owns loading and refreshing. */
data class LibraryState(
  val folders: List<Uri>,
  val titles: List<Title>,
  val progress: Map<String, Progress>,
  val refreshing: Boolean,
  val problems: List<LibraryService.Problem>,
  val message: String?,
  /** The disk usage line for the shelves' header, or null when there is nothing to report. */
  val usage: String? = null,
)

/** Which series is open in the library, and which of its seasons; null shows the shelves. */
data class Browse(val group: String? = null, val season: Int? = null)

/**
 * Titles from the phone's content folders on Manga, Series and Films shelves, grouped from
 * document metadata like the desktop library's posters. A series card opens its tomes or episodes;
 * a title card opens the title where it was left. Refresh rescans the folders; the folder button
 * lists the content folders, where more can be added or removed. A title card shows how far
 * through it the saved position is, which its menu can clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  state: LibraryState,
  images: CardImages,
  browse: Browse,
  shelvesScroll: LazyGridState,
  onBrowse: (Browse) -> Unit,
  onAddFolder: () -> Unit,
  onRemoveFolder: (Uri) -> Unit,
  onRefresh: () -> Unit,
  onQuiz: () -> Unit,
  onNotes: () -> Unit,
  onOpen: (Title) -> Unit,
  onClearProgress: (Title) -> Unit,
) {
  var managing by remember { mutableStateOf(false) }
  if (managing)
    FoldersDialog(
      state.folders,
      onAdd = {
        managing = false
        onAddFolder()
      },
      onRemove = onRemoveFolder,
      onDismiss = { managing = false },
    )
  val shelves = remember(state.titles) { Shelves.shelves(state.titles) }
  val open = browse.group?.let { key -> shelves.flatMap { it.groups }.firstOrNull { it.key == key } }
  BackHandler(enabled = open != null) { onBrowse(Browse()) }
  val cards = Cards(images, state.progress, onOpen, onClearProgress)

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          if (open != null)
            IconButton(onClick = { onBrowse(Browse()) }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library")
            }
        },
        title = { Text(open?.name ?: "petites roues", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          if (state.folders.isNotEmpty())
            IconButton(onClick = onNotes) { Icon(Icons.Outlined.Description, "Notes") }
          if (state.folders.isNotEmpty())
            IconButton(onClick = onQuiz) { Icon(Icons.Outlined.Lightbulb, "Quiz me") }
          if (state.folders.isNotEmpty())
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
              Icon(Icons.Filled.Refresh, "Refresh library")
            }
          IconButton(onClick = { if (state.folders.isEmpty()) onAddFolder() else managing = true }) {
            Icon(Icons.Filled.FolderOpen, "Content folders")
          }
        },
      )
    }
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
      state.message?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
      }
      if (state.folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose the folder Syncthing keeps your titles in.")
            Button(onClick = onAddFolder) { Text("Choose content folder") }
          }
        }
        return@Column
      }
      if (open != null) SeriesView(open, browse.season, cards) { onBrowse(browse.copy(season = it)) }
      else
        PosterGrid(shelvesScroll) {
          state.usage?.let { usage ->
            heading { Text(usage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          if (state.titles.isEmpty() && !state.refreshing)
            heading { Text("No titles yet. Refresh after your content has synced.") }
          for (shelf in shelves) {
            if (shelf.groups.isEmpty()) continue
            heading {
              Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shelf.label, style = MaterialTheme.typography.titleLarge)
                Text(
                  "${shelf.groups.size}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(bottom = 2.dp),
                )
              }
            }
            items(shelf.groups, key = { it.key }) { group ->
              if (group.single) cards.Slot(group.slots.single(), group.name, null)
              else
                cards.Card(
                  group.name,
                  group.count,
                  group.art,
                  group.shelf == "manga",
                  null,
                  emptyList(),
                  onClick = { onBrowse(Browse(group.key)) },
                )
            }
          }
          if (state.problems.isNotEmpty()) {
            heading {
              Text("Not indexed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            for (problem in state.problems)
              heading {
                ListItem(headlineContent = { Text(problem.path) }, supportingContent = { Text(problem.message) })
              }
          }
        }
    }
  }
}

/** A grid of portrait cards that fits as many columns as the screen allows. */
@Composable
private fun PosterGrid(state: LazyGridState, content: LazyGridScope.() -> Unit) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(108.dp),
    state = state,
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize(),
    content = content,
  )
}

/** A full-width row in a [PosterGrid], such as a shelf heading. */
private fun LazyGridScope.heading(content: @Composable () -> Unit) =
  item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(top = 12.dp)) { content() } }

/** The tomes or episodes of one series, with season chips when it has several. */
@Composable
private fun SeriesView(group: ShelfGroup<Title>, chosen: Int?, cards: Cards, onSeason: (Int) -> Unit) {
  val season = if (group.seasons.size > 1) chosen?.takeIf { it in group.seasons } ?: group.seasons.first() else null
  val slots = if (season != null) group.slots.filter { it.entry.season == season } else group.slots
  PosterGrid(rememberLazyGridState()) {
    heading {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.count?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (season != null)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in group.seasons)
              FilterChip(selected = option == season, onClick = { onSeason(option) }, label = { Text("Season $option") })
          }
      }
    }
    items(slots, key = { it.key }) { slot -> cards.Slot(slot, slot.label, slot.detail) }
  }
}

/** Draws library cards and opens titles; shared by the shelves and the series view. */
private class Cards(
  val images: CardImages,
  val progress: Map<String, Progress>,
  val onOpen: (Title) -> Unit,
  val onClearProgress: (Title) -> Unit,
) {
  /** A tome, episode or single title: opens its preferred copy, or a chosen copy from the menu. */
  @Composable
  fun Slot(slot: Slot<Title>, name: String, detail: String?) {
    val entry = slot.entry
    val problem =
      when {
        entry.mediaUri != null -> null
        entry.kind == TitleKind.VIDEO -> "No video"
        else -> "No CBZ"
      }
    val saved = progress[entry.path]
    val resume = saved?.positionSeconds?.takeIf { entry.kind == TitleKind.VIDEO && it >= 1 }
    Card(
      name,
      listOfNotNull(detail, resume?.let { "Resume ${clock(it)}" }).joinToString(" · ").ifEmpty { null },
      slot.art,
      entry.kind == TitleKind.COMIC,
      problem,
      slot.copies,
      onClick = if (problem == null) ({ onOpen(entry) }) else null,
      progress = saved?.fraction?.takeIf { it > 0 }?.toFloat(),
      onClearProgress = if (saved != null) ({ onClearProgress(entry) }) else null,
    )
  }

  /**
   * A portrait card: art (or a tinted placeholder) with a thin [progress] bar along its bottom, a
   * two-line name and a dimmed detail. [onClearProgress] adds a menu to forget the saved position.
   */
  @Composable
  fun Card(
    name: String,
    detail: String?,
    art: CardArt<Title>?,
    comic: Boolean,
    problem: String?,
    copies: List<Title>,
    onClick: (() -> Unit)?,
    progress: Float? = null,
    onClearProgress: (() -> Unit)? = null,
  ) {
    Column(
      Modifier.alpha(if (problem != null) 0.55f else 1f),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        Modifier.fillMaxWidth()
          .aspectRatio(2f / 3f)
          .clip(RoundedCornerShape(8.dp))
          .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      ) {
        Art(CardSource.of(art), name, comic)
        problem?.let {
          Row(
            Modifier.align(Alignment.BottomStart)
              .padding(6.dp)
              .background(Color(0xCC000000), RoundedCornerShape(4.dp))
              .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFB4A9), modifier = Modifier.size(12.dp))
            Text(it, color = Color(0xFFFFB4A9), fontSize = 11.sp)
          }
        }
        progress?.let {
          Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x8C000000))) {
            Box(Modifier.fillMaxWidth(it.coerceIn(0f, 1f)).fillMaxHeight().background(ProgressColor))
          }
        }
      }
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          detail?.let {
            Text(
              it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (copies.size > 1) Copies(copies)
        onClearProgress?.let { More(it) }
      }
    }
  }

  /** The card's own actions: for now, forgetting where the title was left. */
  @Composable
  private fun More(onClearProgress: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
      IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Filled.MoreHoriz, "More options", modifier = Modifier.size(16.dp))
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
          text = { Text("Clear progress") },
          onClick = {
            expanded = false
            onClearProgress()
          },
        )
      }
    }
  }

  /** Lists every copy of a title (content folder · edition) so a specific one can be opened. */
  @Composable
  private fun Copies(copies: List<Title>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
      IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Filled.ContentCopy, "${copies.size} copies", modifier = Modifier.size(16.dp))
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        for (copy in copies)
          DropdownMenuItem(
            enabled = copy.mediaUri != null,
            text = {
              Column {
                Text(copy.folder)
                Text(
                  listOfNotNull(copy.edition, if (copy.mediaUri == null) "No media" else null)
                    .joinToString(" · ")
                    .ifEmpty { "Available" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            onClick = {
              expanded = false
              onOpen(copy)
            },
          )
      }
    }
  }

  @Composable
  private fun Art(source: CardSource?, name: String, comic: Boolean) {
    val image by produceState<ImageBitmap?>(null, source) { value = source?.let { images.load(it) } }
    val bitmap = image
    if (bitmap != null)
      Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else
      Box(Modifier.fillMaxSize().background(placeholder(name)), contentAlignment = Alignment.Center) {
        Icon(
          if (comic) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Movie,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.6f),
          modifier = Modifier.size(28.dp),
        )
      }
  }
}

/** A stable muted colour per name, so placeholders are distinguishable but never random. */
private fun placeholder(name: String): Color {
  var hash = 0
  for (char in name) hash = hash * 31 + char.code
  return Color.hsl(Math.floorMod(hash, 360).toFloat(), 0.28f, 0.26f)
}

/** The content folders, each removable, with a button to add another. */
@Composable
private fun FoldersDialog(
  folders: List<Uri>,
  onAdd: () -> Unit,
  onRemove: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Content folders") },
    text = {
      Column {
        if (folders.isEmpty()) Text("No content folders.")
        for (folder in folders)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label(folder), modifier = Modifier.weight(1f))
            IconButton(onClick = { onRemove(folder) }) { Icon(Icons.Filled.Delete, "Remove folder") }
          }
      }
    },
    confirmButton = { TextButton(onClick = onAdd) { Text("Add folder") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

/** A folder's path on its storage volume, from the granted tree's document ID. */
private fun label(tree: Uri): String {
  val id = DocumentsContract.getTreeDocumentId(tree)
  return id.substringAfter(':').ifEmpty { id }
}

private fun clock(seconds: Double): String {
  val total = seconds.toInt()
  return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
  else "%d:%02d".format(total / 60, total % 60)
}
