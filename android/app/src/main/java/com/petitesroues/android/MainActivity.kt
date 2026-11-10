package com.petitesroues.android

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petitesroues.android.comic.ReadScreen
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.desktop.DesktopDialog
import com.petitesroues.android.desktop.DesktopTitlesScreen
import com.petitesroues.android.desktop.Transfers
import com.petitesroues.android.desktop.mayReachLocalNetwork
import com.petitesroues.android.library.AppFolder
import com.petitesroues.android.library.Browse
import com.petitesroues.android.library.CardImages
import com.petitesroues.android.library.LibraryScreen
import com.petitesroues.android.library.LibraryService
import com.petitesroues.android.library.LibraryViewModel
import com.petitesroues.android.library.Title
import com.petitesroues.android.library.TitleKind
import com.petitesroues.android.library.TitleState
import com.petitesroues.android.library.TitleViewModel
import com.petitesroues.android.library.pagesOnly
import com.petitesroues.android.pages.Section
import com.petitesroues.android.pages.StudyPages
import com.petitesroues.android.player.WatchScreen
import com.petitesroues.android.quiz.QuizShare
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single activity: the library, and the player or comic reader for the open title. The app only
 * reads the content folder; documents are written on the desktop by agents. Hilt injects the
 * app-wide objects it works with.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var settings: Settings
  @Inject lateinit var progress: ProgressStore
  @Inject lateinit var library: LibraryService
  @Inject lateinit var images: CardImages

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme { App() }
    }
  }

  @Composable
  private fun App() {
    val libraryModel: LibraryViewModel = hiltViewModel()
    val state by libraryModel.state.collectAsStateWithLifecycle()
    val section by libraryModel.section.collectAsStateWithLifecycle()
    val pages by libraryModel.pages.collectAsStateWithLifecycle()
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept here so returning from a title restores the open series and the shelves' scroll.
    var browseGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var browseSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    val shelvesScroll = rememberLazyGridState()

    val addFolder =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        tree?.let(libraryModel::addFolder)
      }

    // Back from the system's All files access page: the app's own folder joins the library if
    // allowed.
    val allowFiles =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        libraryModel.refresh()
      }

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
        // Forgetting the desktop leaves nothing to sync with.
        if (settings.desktop == null) titlesOpen = false
      }

    val transfer by Transfers.status.collectAsState()
    // What a sync would fetch badges the desktop button; an unreachable desktop just leaves it as
    // it was.
    val pending by Transfers.pending.collectAsState()
    LaunchedEffect(transfer.finished) {
      if (settings.desktop != null && mayReachLocalNetwork(this@MainActivity))
        withContext(Dispatchers.IO) { runCatching { Transfers.check(this@MainActivity, settings) } }
    }
    if (titlesOpen) {
      DesktopTitlesScreen(settings, onDesktop = { desktopOpen = true }) { titlesOpen = false }
      return
    }

    val open = openPath?.let { path -> state.titles.firstOrNull { it.path == path } }
    if (open == null) {
      LaunchedEffect(Unit) { libraryModel.show() }
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
        onRemoveFolder = libraryModel::removeFolder,
        onRefresh = libraryModel::refresh,
        desktopPending = pending,
        // Paired, the desktop button goes straight to syncing; pairing is a button there.
        onDesktop = {
          if (settings.desktop != null && mayReachLocalNetwork(this@MainActivity)) titlesOpen = true
          else desktopOpen = true
        },
        onGetTitles = { if (settings.desktop == null) desktopOpen = true else titlesOpen = true },
        onAllowFiles = { allowFiles.launch(AppFolder.allowIntent(this@MainActivity)) },
        section = section,
        onSection = libraryModel::selectSection,
        study = {
          StudyPages(
            section,
            pages,
            remembered = settings.generalQuiz.takeIf { section == Section.QUIZZES },
            onBack = { libraryModel.selectSection(Section.LIBRARY) },
            onOpened = { if (section == Section.QUIZZES) settings.generalQuiz = it.key },
            onChat = { quiz, app -> QuizShare.share(this@MainActivity, quiz, app) },
            onCopy = { quiz ->
              getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(
                  ClipData.newPlainText(quiz.title, QuizShare.prompt(this@MainActivity, quiz))
                )
            },
          )
        },
        onOpen = { openPath = it.path },
        onClearProgress = libraryModel::clearProgress,
      )
    } else
      AppTheme(dark = true) {
        TitleScreen(open) { openPath = null }
      }
  }

  /**
   * Shows the player or reader for [title] once [TitleViewModel] has opened it, and a placeholder
   * while it opens or when it can't be.
   */
  @Composable
  private fun TitleScreen(title: Title, onBack: () -> Unit) {
    val model: TitleViewModel = hiltViewModel()
    LaunchedEffect(title.path) { model.open(title) }
    val opened by model.state.collectAsStateWithLifecycle()
    val back = {
      model.close()
      onBack()
    }
    when (val state = opened) {
      is TitleState.Ready if state.title.path == title.path ->
        when {
          title.kind == TitleKind.VIDEO ->
            WatchScreen(state.title, state.document, state.saved, progress, settings, back)
          state.title.pagesOnly ->
            ReadScreen(
              state.title,
              state.document,
              state.saved,
              progress,
              settings,
              back,
              model::flipDirection,
            )
          else -> ReadScreen(state.title, state.document, state.saved, progress, settings, back)
        }
      is TitleState.Failed if state.title.path == title.path -> {
        BackHandler(onBack = back)
        Box(
          Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text("Cannot open ${title.name}: ${state.message}", color = Color(0xFFFFB4A9))
        }
      }
      else ->
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            CircularProgressIndicator(color = Coral, strokeWidth = 2.dp)
            Text(
              "Opening title…",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
    }
  }
}
