package com.petitesroues.android.comic

import android.widget.Toast
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.petitesroues.android.Coral
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.petitesroues.android.study.readyAlignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.petitesroues.android.Settings
import com.petitesroues.android.ThinSlider
import com.petitesroues.android.quiz.QuizBadge
import com.petitesroues.android.quiz.QuizDialog
import com.petitesroues.android.quiz.QuizPack
import com.petitesroues.android.quiz.QuizShare
import com.petitesroues.android.library.LibraryStore
import com.petitesroues.android.library.Title
import com.petitesroues.model.document.ComicBox
import com.petitesroues.model.document.Line
import com.petitesroues.model.document.ComicChapter
import com.petitesroues.model.document.ComicDocument
import com.petitesroues.model.document.ComicPage
import com.petitesroues.model.document.ReadingDirection
import com.petitesroues.model.document.StudyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The comic reader: one page in portrait, a two-page spread in landscape, swiped in the comic's
 * reading direction. Bubble taps open a study card, read aloud before opening on a second tap,
 * or read aloud and open together, according to the remembered listening mode. Tapping elsewhere
 * closes the card or shows the top bar with bubble-outline, listening and landscape controls, and a bottom bar with
 * the page and a slider to jump through the book (filled from the right for right-to-left comics). Landscape shows
 * two-page spreads even when the phone's auto-rotate is off. The page may run under the camera
 * cutout, but the top bar, study card and messages keep clear of it.
 *
 * Pinching zooms the current view (one finger then pans, and swipes stop turning pages); a stylus
 * draws on it, and so does a finger with the top bar's pencil on. Drawings are never saved: they
 * clear, and the zoom resets, when the view changes, or with the buttons shown at the top while
 * there is something to clear or reset.
 *
 * A comic without a study document reads from a pages-only [document] (see [bareComic]): with
 * [onFlipDirection], the top bar swaps the bubble toggles for one that flips the reading direction.
 */
@Composable
fun ReadScreen(
  title: Title,
  document: StudyDocument,
  store: LibraryStore,
  settings: Settings,
  onBack: () -> Unit,
  onFlipDirection: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val comic = document.comic()
  val pages = comic.pages()
  val rtl = comic.readingDirection() == ReadingDirection.RTL
  val reader = remember(title) { CbzReader(context.contentResolver, title.mediaUri!!) }
  val readAloud = remember(document) { ReadAloud(context, document.sourceLanguage()) }
  DisposableEffect(reader, readAloud) {
    onDispose {
      reader.close()
      readAloud.shutdown()
    }
  }
  BackHandler(onBack = onBack)
  var landscape by remember { mutableStateOf(settings.comicLandscape) }
  ImmersiveReading(landscape)

  var page by remember {
    val saved = store.progress(title.path)?.pageId
    mutableIntStateOf(pages.indexOfFirst { it.id() == saved }.coerceAtLeast(0))
  }
  var selected by remember { mutableStateOf<ComicBox?>(null) }
  // The sentence shown when the selected box is a text block.
  var sentence by remember { mutableIntStateOf(0) }
  // The bubble or sentence (and explored phrase) the learner is writing a question about.
  var asking by remember { mutableStateOf<Question?>(null) }
  var quizzing by remember { mutableStateOf(false) }
  // The bubble read aloud by an auto-listen tap, whose card opens on the next tap.
  var heard by remember { mutableStateOf<ComicBox?>(null) }
  var chrome by remember { mutableStateOf(false) }
  var listeningMode by remember { mutableStateOf(settings.listeningMode) }
  var outlines by remember { mutableStateOf(settings.comicOutlines) }
  val spread = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val cutoutSafe = WindowInsets.displayCutout
  val scope = rememberCoroutineScope()
  // The page the slider is being dragged to; the pager follows when the drag ends.
  var scrub by remember { mutableStateOf<Int?>(null) }
  val pageLabel = { index: Int -> pages[index].label().orEmpty().ifBlank { "${index + 1}" } }
  val pageName = { index: Int -> "${pageLabel(index)} / ${pages.size}" }
  val chapters = comic.chapters()
  // Zoom and drawing apply to the current view only and are dropped when it changes.
  var transform by remember { mutableStateOf(PageTransform()) }
  val sketch = remember { Sketch() }
  // Lets a finger draw, for phones without a stylus (a stylus always draws).
  var pencil by remember { mutableStateOf(false) }

  val close = {
    selected = null
    heard = null
    readAloud.stop()
  }
  val tap = { box: ComicBox ->
    if (box.id() != selected?.id()) sentence = 0
    val alreadyHeard = box.id() == heard?.id()
    if (listeningMode.readsAloud(alreadyHeard)) {
      heard = box
      readAloud.say(box.lines().firstOrNull()?.source().orEmpty())
    }
    selected = box.takeIf { listeningMode.opensCard(alreadyHeard) }
  }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    key(spread) {
      val views = remember { Spreads.views(pages.size, spread) }
      val pager = rememberPagerState(initialPage = Spreads.viewOf(views, page)) { views.size }
      // Close the card as soon as the page moves: settledPage only changes once the drag and
      // the snap animation have both finished, which left the card up for a second or so.
      LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.filter { it }.collect { close() }
      }
      LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }
          .collect {
            transform = PageTransform()
            sketch.clear()
          }
      }
      LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }
          .collect { view ->
            val first = views[view].first()
            if (page !in views[view]) page = first
            close()
            store.savePage(title.path, pages[first].id(), (first + 1).toDouble() / pages.size)
          }
      }
      HorizontalPager(
        state = pager,
        reverseLayout = rtl,
        beyondViewportPageCount = 1,
        userScrollEnabled = !transform.zoomed,
        modifier = Modifier.fillMaxSize().keepVerticalSwipesFromPager(),
      ) { view ->
        val shown = views[view].let { if (rtl) it.reversed() else it }
        val current = view == pager.currentPage
        Box(
          if (current)
            Modifier.fillMaxSize().zoomAndDraw({ transform }, { transform = it }, sketch, { pencil })
          else Modifier.fillMaxSize()
        ) {
          Row(
            Modifier.fillMaxSize()
              .graphicsLayer {
                if (current) {
                  transformOrigin = TransformOrigin(0f, 0f)
                  scaleX = transform.scale
                  scaleY = transform.scale
                  translationX = transform.offset.x
                  translationY = transform.offset.y
                }
              }
              .then(if (current) Modifier.sketch(sketch) { transform.scale } else Modifier)
              .pointerInput(Unit) {
                detectTapGestures {
                  if (selected != null || heard != null) close() else chrome = !chrome
                }
              },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            shown.forEachIndexed { index, pageIndex ->
              val gutter =
                when {
                  shown.size == 1 -> Alignment.Center
                  index == 0 -> Alignment.CenterEnd
                  else -> Alignment.CenterStart
                }
              Box(Modifier.weight(1f).fillMaxHeight()) {
                PageView(
                  reader,
                  gutter,
                  pages[pageIndex],
                  pages[pageIndex].boxes(),
                  selected ?: heard,
                  outlines,
                  onSelect = tap,
                )
              }
            }
          }
        }
      }

      if (chrome && selected == null)
        Row(
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures {} }
            .windowInsetsPadding(cutoutSafe.union(WindowInsets.mandatorySystemGestures))
            .padding(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val shown = scrub ?: page
          CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            ThinSlider(
              shown.toFloat(),
              0f..(pages.size - 1).coerceAtLeast(1).toFloat(),
              onValueChange = { scrub = it.roundToInt().coerceIn(0, pages.size - 1) },
              onValueChangeFinished = {
                scrub?.let { target -> scope.launch { pager.scrollToPage(Spreads.viewOf(views, target)) } }
                scrub = null
              },
              modifier = Modifier.weight(1f),
            )
          }
          ChapterLabel(
            pageName(shown),
            chapters,
            Chapters.at(pages, chapters, shown),
            onChapter = { start -> scope.launch { pager.scrollToPage(Spreads.viewOf(views, start)) } },
            pageOf = { chapter -> pages.indexOfFirst { it.id() == chapter.pageId() } },
          )
        }
    }

    selected?.let { box ->
      val lines = box.lines()
      val shown = lines.getOrNull(sentence.coerceIn(0, lines.lastIndex.coerceAtLeast(0))) ?: return@let
      val region = box.regions().firstOrNull()
      val atBottom = region == null || region.y() + region.height() / 2 < 0.5
      Surface(
        color = Color(0xFA20242A),
        contentColor = Color(0xFF9AA6B2),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        modifier =
          Modifier.align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
            .windowInsetsPadding(cutoutSafe)
            .padding(10.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.45f),
      ) {
        Column {
          if (lines.size > 1) SentenceSteps(lines.indexOf(shown), lines.size, onStep = { sentence = it })
          // Keyed by the sentence so stepping starts its card fresh, scrolled to the top.
          key(shown.id()) {
            BubbleCard(
              shown,
              document,
              readAloud,
              onAsk = { phrase -> asking = Question(box, shown, phrase) },
              modifier = Modifier.weight(1f, fill = false),
            )
          }
        }
      }
    }

    // The top bar, and below it Clear drawing / Reset zoom while there is something to clear or reset.
    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
      if (chrome && selected == null)
        Row(
          Modifier.fillMaxWidth()
            .background(Color(0xCC000000))
            .windowInsetsPadding(cutoutSafe)
            .padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
          }
          Text(
            title.name,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { pencil = !pencil }) {
            Icon(
              Icons.Filled.Draw,
              contentDescription = if (pencil) "Stop drawing with a finger" else "Draw with a finger",
              tint = if (pencil) Coral else Color.White,
            )
          }
          // A comic without a study document has no bubbles to outline or read aloud, and no
          // reading direction of its own: the learner picks one instead.
          if (onFlipDirection != null)
            IconButton(onClick = onFlipDirection) {
              Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = if (rtl) "Read left to right" else "Read right to left",
                tint = if (rtl) Coral else Color.White,
              )
            }
          else {
            IconButton(
              onClick = {
                outlines = !outlines
                settings.comicOutlines = outlines
              }
            ) {
              Icon(
                Icons.Filled.CropFree,
                contentDescription = if (outlines) "Hide bubble outlines" else "Show bubble outlines",
                tint = if (outlines) Coral else Color.White,
              )
            }
            // Quizzes are scoped to a chapter, so a volume without chapters offers none.
            if (chapters.isNotEmpty()) QuizBadge(onClick = { quizzing = true }, description = "Quiz me on a chapter in another app")
            IconButton(
              onClick = {
                listeningMode = listeningMode.next()
                settings.listeningMode = listeningMode
                heard = null
                readAloud.stop()
                Toast.makeText(context, listeningMode.label, Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(
                when (listeningMode) {
                  ListeningMode.OFF -> Icons.Filled.HeadsetOff
                  ListeningMode.LISTEN_FIRST -> Icons.Filled.Headphones
                  ListeningMode.LISTEN_AND_OPEN -> Icons.Filled.SpeakerNotes
                },
                contentDescription = "${listeningMode.label}. Switch to: ${listeningMode.next().label}",
                tint = if (listeningMode != ListeningMode.OFF) Coral else Color.White,
              )
            }
          }
          IconButton(
            onClick = {
              landscape = !landscape
              settings.comicLandscape = landscape
            }
          ) {
            Icon(
              if (landscape) Icons.Filled.ScreenLockLandscape else Icons.Filled.ScreenRotation,
              contentDescription = if (landscape) "Follow the phone's rotation" else "Read in landscape",
              tint = if (landscape) Coral else Color.White,
            )
          }
        }
      if (selected == null && (sketch.strokes.isNotEmpty() || transform.zoomed))
        Row(
          Modifier.align(Alignment.End)
            .then(if (chrome) Modifier else Modifier.windowInsetsPadding(cutoutSafe))
            .padding(8.dp)
            .background(Color(0xCC000000), RoundedCornerShape(24.dp))
        ) {
          SketchActions(sketch, transform.zoomed, onResetZoom = { transform = PageTransform() })
        }
    }

    if (quizzing)
      QuizDialog(
        document,
        initialType = settings.quizType,
        onDismiss = { quizzing = false },
        onShare = { type, chapter ->
          settings.quizType = type
          quizzing = false
          val name = chapters[chapter].number()?.let { "chapter $it" } ?: chapters[chapter].title()
          QuizShare.share(context, document, QuizPack.fileName(document, name), type) { rules, chosen ->
            QuizPack.chapter(rules, document, comic, chapter, chosen)
          }
        },
        chapters = chapters.map(Chapters::name),
        initialChapter = Chapters.at(pages, chapters, page).coerceAtLeast(0),
      )

    asking?.let { (box, line, phrase) ->
      AskDialog(
        phrase = phrase?.let { readyAlignment(line, document)?.source()?.getOrNull(it) },
        onDismiss = { asking = null },
        onAsk = { question ->
          asking = null
          val index = pages.indexOfFirst { box in it.boxes() }.coerceAtLeast(0)
          AskShare.share(
            context,
            AskPack.build(document, pageLabel(index), line, pages[index].boxes(), phrase, question),
          )
        },
      )
    }

    readAloud.problem?.let {
      if (listeningMode != ListeningMode.OFF)
        Text(
          it,
          color = Color(0xFFFFB4A9),
          fontSize = 13.sp,
          modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(cutoutSafe).padding(8.dp),
        )
    }
  }
}

/** Clear drawing and Reset zoom, each shown only while there is something to clear or reset. */
@Composable
private fun SketchActions(sketch: Sketch, zoomed: Boolean, onResetZoom: () -> Unit) {
  if (sketch.strokes.isNotEmpty())
    IconButton(onClick = sketch::clear) { Icon(Icons.Filled.DeleteSweep, "Clear drawing", tint = Color.White) }
  if (zoomed) IconButton(onClick = onResetZoom) { Icon(Icons.Filled.FitScreen, "Reset zoom", tint = Color.White) }
}

/** Asks what the learner wants to know about a bubble, before sharing it to a chat app. */
@Composable
private fun AskDialog(phrase: String?, onDismiss: () -> Unit, onAsk: (String) -> Unit) {
  var question by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ask about this bubble") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        phrase?.let { Text("About “$it”", fontSize = 14.sp, color = Color(0xFF9AA6B2)) }
        OutlinedTextField(
          question,
          onValueChange = { question = it },
          placeholder = { Text("What do you want to ask?") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onAsk(question) }) { Text("Share") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** A question being written about [line], held by [box], exploring source [phrase] if any. */
private data class Question(val box: ComicBox, val line: Line, val phrase: Int?)

/** One page image, fitted to its slot against [alignment] (the spine in a spread), with its bubbles as tap targets in normalised coordinates. */
@Composable
private fun PageView(
  reader: CbzReader,
  alignment: Alignment,
  page: ComicPage,
  boxes: List<ComicBox>,
  highlighted: ComicBox?,
  outlined: Boolean,
  onSelect: (ComicBox) -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = alignment) {
    val density = LocalDensity.current
    val slot = with(density) { maxWidth.roundToPx() to maxHeight.roundToPx() }
    val image by
      produceState<Result<ImageBitmap>?>(null, page.entry()) {
        value = withContext(Dispatchers.IO) { runCatching { reader.page(page.entry(), slot.first, slot.second) } }
      }
    val bitmap = image?.getOrNull()
    when {
      image == null -> CircularProgressIndicator()
      bitmap == null ->
        Text(image!!.exceptionOrNull()?.message ?: "Cannot show page", color = Color(0xFFFFB4A9))
      else ->
        BoxWithConstraints(Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
          Image(bitmap, contentDescription = page.label(), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          boxes.forEachIndexed { number, box ->
            val isSelected = box.id() == highlighted?.id()
            box.regions().forEachIndexed { r, region ->
              Box(
                Modifier.offset(maxWidth * region.x().toFloat(), maxHeight * region.y().toFloat())
                  .size(maxWidth * region.width().toFloat(), maxHeight * region.height().toFloat())
                  .then(
                    when {
                      isSelected -> Modifier.border(2.dp, Color(0xA6EB765C), RoundedCornerShape(6.dp))
                      outlined -> Modifier.border(1.dp, Color(0xC067C5FF), RoundedCornerShape(4.dp))
                      else -> Modifier
                    }
                  )
                  .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onSelect(box)
                  }
              ) {
                // The bubble's number on its first region, as the browser's outlines label them.
                if (outlined && r == 0)
                  Text(
                    "${number + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                    modifier =
                      Modifier.background(Color(0xFF245C7E), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                  )
              }
            }
          }
        }
    }
  }
}

/**
 * Keeps gestures that travel more vertically than horizontally away from the pager, so the
 * swipe up for the system bars or the app switcher does not turn the page when it drifts
 * sideways: the pager would otherwise start dragging after a few pixels of sideways movement,
 * however steep the swipe. Taps and mostly horizontal swipes pass through untouched.
 */
private fun Modifier.keepVerticalSwipesFromPager(): Modifier =
  pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      var vertical = false
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        if (!vertical) {
          val dx = abs(change.position.x - down.position.x)
          val dy = abs(change.position.y - down.position.y)
          if (dx > viewConfiguration.touchSlop && dx > dy) break
          vertical = dy > viewConfiguration.touchSlop
        }
        if (vertical) event.changes.forEach { it.consume() }
      }
    }
  }

/**
 * Hides the system bars while reading, and locks landscape when [landscape] is set; otherwise the
 * phone's own rotation applies. A swipe from the edge shows the bars briefly.
 */
@Composable
private fun ImmersiveReading(landscape: Boolean) {
  val view = LocalView.current
  DisposableEffect(view) {
    val window = (view.context as Activity).window
    val controller = WindowCompat.getInsetsController(window, view)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
  }
  DisposableEffect(view, landscape) {
    val activity = view.context as Activity
    activity.requestedOrientation =
      if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
  }
}

/**
 * The page bar's position label: the page, prefixed by its chapter when the volume lists chapters.
 * Tapping it then opens the chapter list, and choosing one jumps to that chapter's first page.
 */
@Composable
private fun ChapterLabel(
  page: String,
  chapters: List<ComicChapter>,
  current: Int,
  onChapter: (Int) -> Unit,
  pageOf: (ComicChapter) -> Int,
) {
  val color = Color(0xFFC8D4E0)
  if (chapters.isEmpty()) {
    Text(page, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    return
  }
  var open by remember { mutableStateOf(false) }
  Box(Modifier.padding(start = 4.dp)) {
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
      val prefix = chapters.getOrNull(current)?.let { Chapters.short(it) + " · " }.orEmpty()
      Text(prefix + page, color = color, fontSize = 13.sp)
      Icon(Icons.Filled.ArrowDropDown, "Chapters", tint = color)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      chapters.forEachIndexed { i, chapter ->
        DropdownMenuItem(
          text = { Text(Chapters.name(chapter), fontWeight = if (i == current) FontWeight.Bold else null) },
          onClick = {
            open = false
            pageOf(chapter).takeIf { it >= 0 }?.let(onChapter)
          },
        )
      }
    }
  }
}

/**
 * A pages-only document for a CBZ no study document references, so [ReadScreen] can show it: its
 * images in [CbzReader.pages] order, each identified by its entry name so reading progress survives
 * a change of direction, with no text, chapters or languages.
 */
fun bareComic(title: Title, pages: List<String>, rtl: Boolean): StudyDocument =
  StudyDocument(
    title.path,
    title.name,
    title.sourceLanguage,
    null,
    null,
    title.series,
    null,
    ComicDocument(
      null,
      if (rtl) ReadingDirection.RTL else ReadingDirection.LTR,
      null,
      pages.map { ComicPage(it, it, null) },
      emptyList(),
    ),
  )
