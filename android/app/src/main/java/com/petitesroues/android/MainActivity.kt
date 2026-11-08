package com.petitesroues.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.petitesroues.android.comic.CbzReader
import com.petitesroues.android.comic.ReadScreen
import com.petitesroues.android.comic.bareComic
import com.petitesroues.android.desktop.DesktopDialog
import com.petitesroues.android.library.AppFolder
import com.petitesroues.android.desktop.DesktopTitlesScreen
import com.petitesroues.android.desktop.Transfers
import com.petitesroues.android.library.Browse
import com.petitesroues.android.library.CardImages
import com.petitesroues.android.library.LibraryScreen
import com.petitesroues.android.library.LibraryService
import com.petitesroues.android.library.LibraryState
import com.petitesroues.android.library.LibraryUsage
import com.petitesroues.android.quiz.GeneralQuizDialog
import com.petitesroues.android.pages.MarkdownPage
import com.petitesroues.android.pages.MarkdownPageScanner
import com.petitesroues.android.pages.NotesScreen
import com.petitesroues.android.quiz.QuizShare
import com.petitesroues.android.library.LibraryStore
import com.petitesroues.android.library.Title
import com.petitesroues.android.library.TitleKind
import com.petitesroues.android.player.WatchScreen
import com.petitesroues.model.document.StudyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single activity: the library, and the player or comic reader for the open title. The app
 * only reads the content folder; documents are written on the desktop by agents.
 */
class MainActivity : ComponentActivity() {
  private val settings by lazy { Settings(this) }
  private val store by lazy { LibraryStore(this) }
  private val library by lazy { LibraryService(contentResolver, store) }
  private val images by lazy { CardImages(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme { App() }
    }
  }

  @Composable
  private fun App() {
    val scope = rememberCoroutineScope()
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept here so returning from a title restores the open series and the shelves' scroll.
    var browseGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var browseSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    val shelvesScroll = rememberLazyGridState()
    var state by remember {
      mutableStateOf(
        LibraryState(settings.libraryFolders, emptyList(), emptyMap(), false, emptyList(), null)
      )
    }

    suspend fun reload() {
      val (titles, progress) = withContext(Dispatchers.IO) { store.titles() to store.progress() }
      val usage =
        withContext(Dispatchers.IO) {
          runCatching { LibraryUsage.line(settings.libraryBytes, LibraryUsage.free(this@MainActivity, settings.libraryFolders)) }
            .getOrNull()
            ?.ifBlank { null }
        }
      state = state.copy(titles = titles, progress = progress, usage = usage)
    }

    fun refresh() {
      val trees = settings.libraryFolders
      state = state.copy(folders = trees, refreshing = true, message = null)
      scope.launch {
        try {
          val started = System.nanoTime()
          val refreshed =
            withContext(Dispatchers.IO) {
              library
                .refresh(trees) { progress -> scope.launch { state = state.copy(refreshProgress = progress) } }
                .also { images.clear() }
            }
          Log.i("Library", "Refreshed ${trees.size} folders in ${(System.nanoTime() - started) / 1_000_000} ms")
          settings.libraryBytes = refreshed.bytes
          state = state.copy(problems = refreshed.problems)
          reload()
        } catch (failure: Exception) {
          state = state.copy(message = "Refresh failed: ${failure.message}")
        } finally {
          state = state.copy(refreshing = false, refreshProgress = null)
        }
      }
    }

    val addFolder =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) return@rememberLauncherForActivityResult
        contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (tree !in settings.contentTrees) settings.contentTrees += tree
        refresh()
      }

    fun removeFolder(tree: Uri) {
      settings.contentTrees -= tree
      if (settings.downloadFolder == tree) settings.downloadFolder = null
      // A folder chosen for downloads was granted for writing too.
      runCatching {
        contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      }
        .recoverCatching { contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
      refresh()
    }

    // The general quizzes, read from the content folders each time Quiz me opens; null when closed.
    var quizzes by remember { mutableStateOf<List<MarkdownPage>?>(null) }
    quizzes?.let { found ->
      GeneralQuizDialog(
        found,
        settings.generalQuiz,
        onDismiss = { quizzes = null },
        onShare = { quiz ->
          settings.generalQuiz = quiz.key
          quizzes = null
          QuizShare.share(this@MainActivity, quiz)
        },
      )
    }

    // Back from the system's All files access page: the app's own folder joins the library if allowed.
    val allowFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    var desktopOpen by remember { mutableStateOf(false) }
    var titlesOpen by rememberSaveable { mutableStateOf(false) }
    if (desktopOpen)
      DesktopDialog(
        settings,
        onGetTitles = {
          desktopOpen = false
          titlesOpen = true
        },
      ) {
        desktopOpen = false
      }

    // Each title copied from the desktop shows up once the library has refreshed.
    val transfer by Transfers.status.collectAsState()
    LaunchedEffect(transfer.finished) { if (transfer.finished > 0) refresh() }
    if (titlesOpen) {
      DesktopTitlesScreen(settings) { titlesOpen = false }
      return
    }

    // The study notes screen replaces the library while open; its notes are read on opening.
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<MarkdownPage>?>(null) }
    if (notesOpen) {
      LaunchedEffect(Unit) {
        notes = withContext(Dispatchers.IO) { MarkdownPageScanner(contentResolver).scan(settings.libraryFolders, MarkdownPage.NOTES) }
      }
      NotesScreen(notes) {
        notesOpen = false
        notes = null
      }
      return
    }

    val open = openPath?.let { path -> state.titles.firstOrNull { it.path == path } }
    if (open == null) {
      LaunchedEffect(Unit) { reload() }
      LibraryScreen(
        state,
        images,
        Browse(browseGroup, browseSeason),
        shelvesScroll,
        onBrowse = {
          browseGroup = it.group
          browseSeason = it.season
        },
        onAddFolder = { addFolder.launch(null) },
        onRemoveFolder = ::removeFolder,
        onRefresh = ::refresh,
        onDesktop = { desktopOpen = true },
        onGetTitles = { if (settings.desktop == null) desktopOpen = true else titlesOpen = true },
        onAllowFiles = { allowFiles.launch(AppFolder.allowIntent(this@MainActivity)) },
        onNotes = { notesOpen = true },
        onQuiz = {
          scope.launch {
            quizzes = withContext(Dispatchers.IO) { MarkdownPageScanner(contentResolver).scan(settings.libraryFolders, MarkdownPage.QUIZZES) }
          }
        },
        onOpen = { openPath = it.path },
        onClearProgress = { title ->
          scope.launch {
            withContext(Dispatchers.IO) { store.clearProgress(title.path) }
            reload()
          }
        },
      )
    } else
      AppTheme(dark = true) {
        TitleScreen(open) { openPath = null }
      }
  }

  /**
   * Loads the title's document, then shows the player or reader for it. A comic without a study
   * document reads from a pages-only one built from its CBZ, in the direction chosen for it.
   */
  @Composable
  private fun TitleScreen(title: Title, onBack: () -> Unit) {
    val bare = title.documentUri == null && title.kind == TitleKind.COMIC
    var rtl by remember(title) { mutableStateOf(bare && settings.comicRtl(title.path)) }
    var loaded by remember(title) { mutableStateOf<Result<StudyDocument>?>(null) }
    LaunchedEffect(title, rtl) {
      loaded = withContext(Dispatchers.IO) {
        runCatching {
          if (bare)
            CbzReader(contentResolver, checkNotNull(title.mediaUri) { "its CBZ is missing" }).use {
              bareComic(title, it.pages(), rtl)
            }
          else library.read(checkNotNull(title.documentUri) { "it has no study document" })
        }
      }
    }
    val document = loaded?.getOrNull()
    when {
      loaded == null ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator(color = Coral, strokeWidth = 2.dp)
            Text("Opening title…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      document == null -> {
        androidx.activity.compose.BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
          Text("Cannot open ${title.name}: ${loaded!!.exceptionOrNull()?.message}", color = Color(0xFFFFB4A9))
        }
      }
      title.kind == TitleKind.VIDEO -> WatchScreen(title, document, store, settings, onBack)
      bare ->
        ReadScreen(title, document, store, settings, onBack) {
          rtl = !rtl
          settings.setComicRtl(title.path, rtl)
        }
      else -> ReadScreen(title, document, store, settings, onBack)
    }
  }
}
