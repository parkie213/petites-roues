package app.petitesroues.android

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.petitesroues.android.comic.ReadScreen
import app.petitesroues.android.comic.ReaderViewModel
import app.petitesroues.android.desktop.DesktopDialog
import app.petitesroues.android.desktop.DesktopTitlesScreen
import app.petitesroues.android.library.AppFolder
import app.petitesroues.android.library.Browse
import app.petitesroues.android.library.CardImages
import app.petitesroues.android.library.LibraryScreen
import app.petitesroues.android.library.LibraryViewModel
import app.petitesroues.android.library.TitleKind
import app.petitesroues.android.library.TitleUiState
import app.petitesroues.android.library.TitleViewModel
import app.petitesroues.android.pages.Section
import app.petitesroues.android.pages.StudyPages
import app.petitesroues.android.player.PlayerViewModel
import app.petitesroues.android.player.WatchScreen
import app.petitesroues.android.quiz.QuizShare
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single activity: the library, the player or comic reader for an open title, and the desktop's
 * titles, as a back stack of routes ([LibraryRoute], [TitleRoute], [DesktopTitlesRoute]) that
 * survives the app being stopped. The app only reads the content folder; documents are written on
 * the desktop by agents. Hilt injects the app-wide objects it works with.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var settings: Settings
  @Inject lateinit var images: CardImages

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme { App() }
    }
  }

  @Composable
  private fun App() {
    // The library outlives its screen, so a refresh carries on while a title or the desktop is
    // open.
    val libraryModel: LibraryViewModel = hiltViewModel()
    val backStack = rememberNavBackStack(LibraryRoute)
    val openDesktopTitles = {
      if (backStack.lastOrNull() != DesktopTitlesRoute) backStack.add(DesktopTitlesRoute)
    }

    var desktopOpen by remember { mutableStateOf(false) }
    if (desktopOpen)
      DesktopDialog(
        onGetTitles = {
          desktopOpen = false
          openDesktopTitles()
        },
        onDismiss = {
          desktopOpen = false
          // Forgetting the desktop leaves nothing to sync with.
          if (!libraryModel.paired) backStack.removeAll { it == DesktopTitlesRoute }
        },
      )

    val noTransition = ContentTransform(EnterTransition.None, ExitTransition.None)
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryDecorators =
        listOf(
          rememberSaveableStateHolderNavEntryDecorator(),
          rememberViewModelStoreNavEntryDecorator(),
        ),
      transitionSpec = { noTransition },
      popTransitionSpec = { noTransition },
      predictivePopTransitionSpec = { noTransition },
      entryProvider =
        entryProvider {
          entry<LibraryRoute> {
            Library(
              libraryModel,
              onOpen = { backStack.add(TitleRoute(it)) },
              // Paired, the desktop button goes straight to syncing; pairing is a button there.
              onDesktop = {
                if (libraryModel.desktopReachable) openDesktopTitles() else desktopOpen = true
              },
              onGetTitles = {
                if (libraryModel.paired) openDesktopTitles() else desktopOpen = true
              },
            )
          }
          entry<TitleRoute> { route ->
            AppTheme(dark = true) { TitleScreen(route.path) { backStack.removeLastOrNull() } }
          }
          entry<DesktopTitlesRoute> {
            DesktopTitlesScreen(
              onDesktop = { desktopOpen = true },
              onBack = { backStack.removeLastOrNull() },
            )
          }
        },
    )
  }

  /** The library screen over [model], opening the title at a path with [onOpen]. */
  @Composable
  private fun Library(
    model: LibraryViewModel,
    onOpen: (String) -> Unit,
    onDesktop: () -> Unit,
    onGetTitles: () -> Unit,
  ) {
    val state by model.state.collectAsStateWithLifecycle()
    val section by model.section.collectAsStateWithLifecycle()
    val pages by model.pages.collectAsStateWithLifecycle()
    val desktopPending by model.desktopPending.collectAsStateWithLifecycle()
    // Saved with the library's place on the back stack, so returning from a title restores the
    // open series and the shelves' scroll.
    var browseGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var browseSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    val shelvesScroll = rememberLazyGridState()

    val addFolder =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        tree?.let(model::addFolder)
      }
    // Back from the system's All files access page: the app's own folder joins the library if
    // allowed.
    val allowFiles =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.refresh()
      }

    LaunchedEffect(Unit) { model.onShown() }
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
      onRemoveFolder = model::removeFolder,
      onRefresh = model::refresh,
      desktopPending = desktopPending,
      onDesktop = onDesktop,
      onGetTitles = onGetTitles,
      onAllowFiles = { allowFiles.launch(AppFolder.allowIntent(this@MainActivity)) },
      section = section,
      onSection = model::selectSection,
      study = {
        StudyPages(
          section,
          pages,
          remembered = settings.generalQuiz.takeIf { section == Section.QUIZZES },
          onBack = { model.selectSection(Section.LIBRARY) },
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
      onOpen = { onOpen(it.path) },
      onClearProgress = model::clearProgress,
    )
  }

  /**
   * The player or reader for the title at [path] once [TitleViewModel] has opened it, and a
   * placeholder while it opens or when it can't be.
   */
  @Composable
  private fun TitleScreen(path: String, onBack: () -> Unit) {
    val model = hiltViewModel<TitleViewModel, TitleViewModel.Factory> { it.create(path) }
    val opened by model.state.collectAsStateWithLifecycle()
    when (val state = opened) {
      is TitleUiState.Ready ->
        when {
          state.title.kind == TitleKind.VIDEO -> {
            val player =
              hiltViewModel<PlayerViewModel, PlayerViewModel.Factory> {
                it.create(state.title, state.document, state.saved)
              }
            WatchScreen(player, onBack)
          }
          else -> {
            val reader =
              hiltViewModel<ReaderViewModel, ReaderViewModel.Factory> {
                it.create(state.title, state.document.sourceLanguage(), state.saved)
              }
            ReadScreen(
              reader,
              state.document,
              onBack,
              onFlipDirection = model::flipDirection.takeIf { state.title.pagesOnly },
            )
          }
        }
      is TitleUiState.Failed -> {
        BackHandler(onBack = onBack)
        Box(
          Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text("Cannot open ${state.name}: ${state.message}", color = Color(0xFFFFB4A9))
        }
      }
      TitleUiState.Opening ->
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
