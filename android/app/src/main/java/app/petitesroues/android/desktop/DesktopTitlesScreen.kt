package app.petitesroues.android.desktop

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.petitesroues.android.Settings
import app.petitesroues.android.library.AppFolder
import app.petitesroues.android.library.LibraryUsage
import app.petitesroues.sync.SyncStatus
import java.text.Normalizer

/**
 * Syncing with the paired desktop: one row per series (or standalone title, or the notes and
 * quizzes), saying how much of it is on the phone. Its checkbox follows it ([Settings.followed]):
 * ticking starts a sync that copies its missing titles, and later syncs fetch the ones the desktop
 * adds. Every sync also fetches updates (a changed document or page) to whatever is already on the
 * phone, followed or not. Tapping a series opens its titles, each with a checkbox of its own to
 * copy just that one ([Settings.chosen]). Opening the screen checks the desktop; Sync fetches what
 * it found ([Transfers]). Titles land in the download folder, the app's own [AppFolder] once All
 * files access is allowed, or another folder chosen with the picker (granted for writing, and added
 * to the content folders), and appear in the library as each finishes. [onDesktop] opens the
 * pairing dialog. [DesktopTitlesViewModel] holds all of this; the screen keeps only which series
 * are open and the search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTitlesScreen(
  onDesktop: () -> Unit,
  onBack: () -> Unit,
  model: DesktopTitlesViewModel = hiltViewModel(),
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val state by model.state.collectAsStateWithLifecycle()
  val transfer by model.transfer.collectAsStateWithLifecycle()
  val folder = state.folder
  val sync = state.sync
  // The series whose titles are shown.
  var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
  var search by rememberSaveable { mutableStateOf("") }

  val chooseFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
      tree?.let(model::chooseFolder)
    }
  // Back from the system's All files access page, allowed or not.
  val allowFiles =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      model.fileAccessChanged()
    }
  // Notifications show the download's progress; it runs whether or not they are allowed. Ticking a
  // series or title asks too, since the sync it starts follows the check.
  val askThenSync =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.sync() }
  val askNotifications =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  fun follow(group: SyncStatus.Group, follow: Boolean) {
    model.follow(group.key(), follow)
    if (follow) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  fun choose(entry: SyncStatus.Entry, choose: Boolean) {
    model.choose(entry.title().id(), choose)
    if (choose) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  /** Each group's row, its titles when opened, and a divider. */
  fun LazyListScope.groups(list: List<SyncStatus.Group>) = list.forEach { group ->
    val series = group.entries().size > 1
    val open = series && group.key() in opened
    item(key = group.key()) {
      GroupRow(
        group,
        open = if (series) open else null,
        onOpen = { opened = if (open) opened - group.key() else opened + group.key() },
      ) {
        follow(group, it)
      }
    }
    if (open)
      items(group.entries(), key = { "title:" + it.title().id() }) { entry ->
        EntryRow(entry, group.followed()) { choose(entry, it) }
      }
    item(key = "divider:" + group.key()) { HorizontalDivider() }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library") }
        },
        title = {
          Text(
            "Sync with ${state.desktop ?: "the desktop"}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        actions = {
          IconButton(onClick = onDesktop) { Icon(Icons.Filled.Computer, "Desktop pairing") }
        },
      )
    },
    bottomBar = {
      Surface(tonalElevation = 3.dp) {
        Column(
          Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FolderLine(
            folder,
            custom = state.customFolder,
            onAllow = { allowFiles.launch(AppFolder.allowIntent(context)) },
            onChoose = { chooseFolder.launch(null) },
            onDefault = model::useDefaultFolder,
          )
          if (transfer.running) {
            if (transfer.total > 0)
              LinearProgressIndicator(
                progress = { transfer.done.toFloat() / transfer.total },
                Modifier.fillMaxWidth(),
              )
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                transfer.message
                  ?: "${transfer.title ?: "Starting"} · ${LibraryUsage.format(transfer.done)} of ${LibraryUsage.format(transfer.total)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
              )
              TextButton(onClick = model::stop) { Text("Stop") }
            }
          } else {
            transfer.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val fetch = sync?.toSync().orEmpty()
            Button(
              onClick = { askThenSync.launch(Manifest.permission.POST_NOTIFICATIONS) },
              enabled = fetch.isNotEmpty() && folder != null,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                when {
                  sync == null -> "Sync"
                  fetch.isEmpty() -> "Up to date"
                  else -> "Sync ${titles(fetch.size)} · ${LibraryUsage.format(sync.remaining())}"
                }
              )
            }
          }
        }
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      val groups = sync?.groups()
      when {
        folder == null ->
          Text("Allow file access to sync titles into ${AppFolder.LABEL}.", Modifier.padding(24.dp))
        state.problem != null -> Text(state.problem!!, Modifier.padding(24.dp))
        groups == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        groups.isEmpty() -> Text("The desktop has no titles to copy yet.", Modifier.padding(24.dp))
        else -> {
          val shown = groups.filter { matches(it, search) }
          // What a sync would fetch comes first, so the badge leads straight to it.
          val (waiting, rest) = shown.partition { it.toSync().isNotEmpty() }
          Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
              search,
              { search = it },
              Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              placeholder = { Text("Search titles") },
              leadingIcon = { Icon(Icons.Filled.Search, null) },
              trailingIcon = {
                if (search.isNotEmpty())
                  IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, "Clear search") }
              },
              singleLine = true,
            )
            LazyColumn(Modifier.fillMaxSize()) {
              if (shown.isEmpty())
                item { Text("No titles match “$search”.", Modifier.padding(24.dp)) }
              if (waiting.isNotEmpty()) item(key = "heading:waiting") { Heading("To sync") }
              if (waiting.isNotEmpty() && rest.isNotEmpty()) {
                groups(waiting)
                item(key = "heading:rest") { Heading("Everything else") }
                groups(rest)
              } else groups(shown)
            }
          }
        }
      }
    }
  }
}

/** A heading above part of the list. */
@Composable
private fun Heading(text: String) {
  Text(
    text,
    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
  )
}

/**
 * Whether a series or standalone title matches the search: its name or any of its titles contains
 * every word, ignoring case and accents.
 */
private fun matches(group: SyncStatus.Group, search: String): Boolean {
  val words = fold(search).split(' ').filter { it.isNotEmpty() }
  if (words.isEmpty()) return true
  val names = (listOf(group.name()) + group.entries().map { it.title().title() }).map(::fold)
  return names.any { name -> words.all { it in name } }
}

private fun fold(text: String): String =
  Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

private fun titles(count: Int) = "$count ${if (count == 1) "title" else "titles"}"

/**
 * A series or standalone title: its follow checkbox, its name and where it stands on the phone. A
 * series ([open] not null) opens and closes its titles when tapped; a standalone title is ticked.
 * One with something to sync is tinted, with its summary in the accent colour.
 */
@Composable
private fun GroupRow(
  group: SyncStatus.Group,
  open: Boolean?,
  onOpen: () -> Unit,
  onFollow: (Boolean) -> Unit,
) {
  val followable = group.followable()
  val waiting = group.toSync().isNotEmpty()
  Row(
    Modifier.fillMaxWidth()
      .background(if (waiting) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .clickable(enabled = open != null || followable) {
        if (open != null) onOpen() else onFollow(!group.followed())
      }
      .padding(start = 4.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = group.followed(), onCheckedChange = onFollow, enabled = followable)
    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
      Text(
        group.name(),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      val problem = group.entries().singleOrNull()?.title()?.problem()
      Text(
        problem ?: summary(group),
        style = MaterialTheme.typography.bodySmall,
        color =
          when {
            problem != null -> MaterialTheme.colorScheme.error
            waiting -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
      )
    }
    if (open != null)
      Icon(
        if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        if (open) "Hide titles" else "Show titles",
      )
  }
}

/**
 * One title of an opened series. Its checkbox copies just this title; it is ticked and fixed once
 * the title is on the phone, or while the series is [followed], which copies it anyway.
 */
@Composable
private fun EntryRow(entry: SyncStatus.Entry, followed: Boolean, onChoose: (Boolean) -> Unit) {
  val state = entry.state()
  val choosable = state == SyncStatus.State.MISSING && !followed
  val problem = entry.title().problem()
  Row(
    Modifier.fillMaxWidth()
      .clickable(enabled = choosable) { onChoose(!entry.chosen()) }
      .padding(start = 28.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked =
        state == SyncStatus.State.UPDATE ||
          state == SyncStatus.State.UP_TO_DATE ||
          entry.chosen() ||
          (followed && state == SyncStatus.State.MISSING),
      onCheckedChange = onChoose,
      enabled = choosable,
    )
    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
      Text(entry.title().title(), maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        when (state) {
          SyncStatus.State.PROBLEM -> problem!!
          SyncStatus.State.UP_TO_DATE -> "On phone"
          SyncStatus.State.UPDATE -> "Update available · ${LibraryUsage.format(entry.remaining())}"
          SyncStatus.State.MISSING -> LibraryUsage.format(entry.remaining())
        },
        style = MaterialTheme.typography.bodySmall,
        color =
          if (problem != null) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Where a group stands: for a single title, whether it is on the phone; for a series, how many of
 * its titles are, then what a sync adds and updates, what is left out until it is followed, and how
 * many the desktop cannot offer.
 */
private fun summary(group: SyncStatus.Group): String {
  val entries = group.entries()
  val missing = group.count(SyncStatus.State.MISSING)
  val updates = group.count(SyncStatus.State.UPDATE)
  val unavailable = group.count(SyncStatus.State.PROBLEM)
  val fetch = LibraryUsage.format(group.toSync().sumOf { it.remaining() })
  val notFetched =
    LibraryUsage.format(
      entries
        .filter { it.state() == SyncStatus.State.MISSING && !group.fetches(it) }
        .sumOf { it.remaining() }
    )
  if (entries.size == 1)
    return when {
      updates == 1 -> "Update available · $fetch"
      missing == 1 && group.toSync().isNotEmpty() -> "To copy · $fetch"
      missing == 1 -> "Not on phone · $notFetched"
      else -> "On phone"
    }
  val onPhone = entries.size - missing - unavailable
  return buildList {
      add("$onPhone of ${entries.size} on phone")
      val chosen = group.entries().count { it.chosen() && it.state() == SyncStatus.State.MISSING }
      if (missing > 0 && group.followed()) add("$missing new")
      else {
        if (chosen > 0) add("$chosen to copy")
        if (missing > chosen) add("${missing - chosen} more · $notFetched")
      }
      if (updates > 0) add("$updates ${if (updates == 1) "update" else "updates"}")
      if (group.toSync().isNotEmpty()) add("$fetch to fetch")
      if (unavailable > 0) add("$unavailable unavailable")
    }
    .joinToString(" · ")
}

/**
 * Where titles are copied to. Without a folder, it asks for All files access for the app's own;
 * [custom] when another folder was chosen, which can be dropped to go back to the app's own.
 */
@Composable
private fun FolderLine(
  folder: Uri?,
  custom: Boolean,
  onAllow: () -> Unit,
  onChoose: () -> Unit,
  onDefault: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      folder?.let { "Copying into ${folderLabel(it)}" }
        ?: "Allow file access so titles can be copied into ${AppFolder.LABEL}.",
      Modifier.weight(1f),
      style = MaterialTheme.typography.bodySmall,
    )
    if (folder == null) TextButton(onClick = onAllow) { Text("Allow") }
    else if (custom) TextButton(onClick = onDefault) { Text("Use default") }
    else TextButton(onClick = onChoose) { Text("Change") }
  }
}
