package com.petitesroues.android.desktop

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petitesroues.android.Settings
import com.petitesroues.android.library.AppFolder
import com.petitesroues.android.library.LibraryUsage
import com.petitesroues.model.sync.DesktopTitle
import com.petitesroues.sync.DesktopClient
import com.petitesroues.sync.TransferPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A desktop title as listed here, compared with the download folder: [remaining] bytes still to
 * fetch, none when it is up to date; [update] when only its document or pages changed.
 */
private class Listed(val title: DesktopTitle, val remaining: Long, val upToDate: Boolean, val update: Boolean) {
  val choosable: Boolean
    get() = title.problem() == null && !upToDate
}

/**
 * The paired desktop's titles, grouped by series, to tick and copy into the download folder
 * ([Transfers]): the app's own [AppFolder] once All files access is allowed, or another folder
 * chosen with the picker (granted for writing, and added to the content folders). Copied titles
 * appear in the library as each finishes. A title already there and unchanged is marked "On
 * phone"; one whose document (or, for notes and quizzes, a page) changed on the desktop offers an
 * update, which fetches only what changed. One the phone could not link (no matching media, or
 * linked by hand on the desktop) shows why and cannot be ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopTitlesScreen(settings: Settings, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val transfer by Transfers.status.collectAsState()
  var folder by remember { mutableStateOf(downloadFolder(settings)) }
  var listed by remember { mutableStateOf<List<Listed>?>(null) }
  var problem by remember { mutableStateOf<String?>(null) }
  var selected by rememberSaveable { mutableStateOf(listOf<String>()) }

  val chooseFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
      if (tree == null) return@rememberLauncherForActivityResult
      context.contentResolver.takePersistableUriPermission(
        tree,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
      settings.downloadFolder = tree
      if (tree !in settings.contentTrees) settings.contentTrees += tree
      folder = tree
    }
  // Back from the system's All files access page, allowed or not.
  val allowFiles =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { folder = downloadFolder(settings) }
  // Notifications show the download's progress; it runs whether or not they are allowed.
  val askNotifications =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      val ids = selected.toSet()
      val bytes = listed.orEmpty().filter { it.title.id() in ids }.sumOf { it.remaining }
      Transfers.start(context, ids, bytes)
      selected = emptyList()
    }

  LaunchedEffect(folder, transfer.finished) {
    if (settings.desktop == null) return@LaunchedEffect
    val result =
      withContext(Dispatchers.IO) {
        runCatching {
          val titles = reachDesktop(context, settings, DesktopClient()::titles)
          val target = folder?.let { transferFolder(context.contentResolver, it) }
          titles.map { title ->
            if (target == null || title.problem() != null) Listed(title, title.bytes(), upToDate = false, update = false)
            else {
              val plan = TransferPlan.of(listOf(title), target)
              val steps = plan.steps()
              Listed(title, plan.remaining(), steps.isEmpty(), steps.isNotEmpty() && steps.all { it.file().whole() })
            }
          }
        }
      }
    result
      .onSuccess {
        listed = it
        problem = null
      }
      .onFailure {
        Log.w("Transfers", "Listing the desktop's titles failed", it)
        problem = "Could not list the desktop's titles: ${it.message ?: it.javaClass.simpleName}"
      }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library") } },
        title = { Text("Get from ${settings.desktop?.name() ?: "the desktop"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
      )
    },
    bottomBar = {
      Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FolderLine(
            folder,
            custom = settings.downloadFolder != null,
            onAllow = { allowFiles.launch(AppFolder.allowIntent(context)) },
            onChoose = { chooseFolder.launch(null) },
            onDefault = {
              settings.downloadFolder = null
              folder = downloadFolder(settings)
            },
          )
          if (transfer.running) {
            if (transfer.total > 0) LinearProgressIndicator(progress = { transfer.done.toFloat() / transfer.total }, Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                transfer.message
                  ?: "${transfer.title ?: "Starting"} · ${LibraryUsage.format(transfer.done)} of ${LibraryUsage.format(transfer.total)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
              )
              TextButton(onClick = { Transfers.stop(context) }) { Text("Stop") }
            }
          } else {
            transfer.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val ids = selected.toSet()
            val chosen = listed.orEmpty().filter { it.title.id() in ids }
            val updates = listed.orEmpty().filter { it.update && it.choosable }.map { it.title.id() }
            if (updates.isNotEmpty() && !ids.containsAll(updates))
              TextButton(onClick = { selected = (selected + updates).distinct() }) {
                Text("Tick ${updates.size} ${if (updates.size == 1) "update" else "updates"}")
              }
            Button(
              onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
              enabled = chosen.isNotEmpty() && folder != null,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (chosen.isEmpty()) "Tick titles to copy"
                else "Copy ${chosen.size} ${if (chosen.size == 1) "title" else "titles"} · ${LibraryUsage.format(chosen.sumOf { it.remaining })}"
              )
            }
          }
        }
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      val titles = listed
      when {
        problem != null -> Text(problem!!, Modifier.padding(24.dp))
        titles == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        titles.isEmpty() -> Text("The desktop has no titles to copy yet.", Modifier.padding(24.dp))
        else ->
          LazyColumn(Modifier.fillMaxSize()) {
            titles.groupBy { it.title.series() ?: it.title.title() }.forEach { (series, group) ->
              val choosable = group.filter { it.choosable }.map { it.title.id() }
              item(key = "series:$series") {
                val ticked = choosable.count { it in selected }
                Row(
                  Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TriStateCheckbox(
                    state =
                      when {
                        choosable.isEmpty() || ticked == 0 -> ToggleableState.Off
                        ticked == choosable.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                      },
                    onClick = {
                      selected = if (ticked == choosable.size) selected - choosable.toSet() else (selected + choosable).distinct()
                    },
                    enabled = choosable.isNotEmpty(),
                  )
                  Text(series, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                  Text(LibraryUsage.format(group.sumOf { it.title.bytes() }), style = MaterialTheme.typography.bodySmall)
                }
              }
              items(group, key = { it.title.id() }) { entry ->
                TitleRow(entry, entry.title.id() in selected) { tick ->
                  selected = if (tick) selected + entry.title.id() else selected - entry.title.id()
                }
              }
              item(key = "divider:$series") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            }
          }
      }
    }
  }
}

@Composable
private fun TitleRow(entry: Listed, ticked: Boolean, onTick: (Boolean) -> Unit) {
  val title = entry.title
  val choosable = entry.choosable
  Row(
    Modifier.fillMaxWidth().clickable(enabled = choosable) { onTick(!ticked) }.padding(start = 28.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = ticked || entry.upToDate, onCheckedChange = onTick, enabled = choosable)
    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
      Text(title.title(), maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        when {
          title.problem() != null -> title.problem()
          entry.upToDate -> "On phone"
          entry.update -> "Update available · ${LibraryUsage.format(entry.remaining)}"
          title.kind() == DesktopTitle.PAGES -> "${title.files().size} pages · ${LibraryUsage.format(title.bytes())}"
          else -> LibraryUsage.format(title.bytes())
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (title.problem() != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Where titles are copied to. Without a folder, it asks for All files access for the app's own;
 * [custom] when another folder was chosen, which can be dropped to go back to the app's own.
 */
@Composable
private fun FolderLine(folder: Uri?, custom: Boolean, onAllow: () -> Unit, onChoose: () -> Unit, onDefault: () -> Unit) {
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
