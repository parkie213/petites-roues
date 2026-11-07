package com.petitesroues.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petitesroues.model.document.PhraseAlignment

/** The browser's phrase colours, by link index. */
private val phraseColors =
  listOf(
    Color(0xFF89C9B3),
    Color(0xFFB8A5ED),
    Color(0xFFE8B97E),
    Color(0xFF8DBDE5),
    Color(0xFFE89DAC),
    Color(0xFFBDCD85),
    Color(0xFF91CBD2),
  )

fun phraseColor(link: Int): Color =
  if (link < 0) Color.Transparent else phraseColors[link % phraseColors.size]

/** How a subtitle line of phrases is drawn on its dark chips. */
data class PhraseLook(
  val chip: Color,
  val activeChip: Color,
  val underline: Dp,
  val padding: Dp,
)

val SubtitleLook = PhraseLook(Color(0xDD000000), Color(0xF520262A), 3.dp, 6.dp)

/**
 * A source line split into tappable phrases, each underlined in its link's colour. The active
 * phrase is outlined; [onTap] receives the phrase index.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhraseLine(
  alignment: PhraseAlignment,
  active: Int?,
  style: TextStyle,
  look: PhraseLook,
  onTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
  centred: Boolean = false,
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement =
      if (centred) Arrangement.spacedBy(7.dp, androidx.compose.ui.Alignment.CenterHorizontally)
      else Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    alignment.source().forEachIndexed { index, phrase ->
      val colour = phraseColor(alignment.linkOfSource(index))
      val isActive = active == index
      val shape = RoundedCornerShape(4.dp)
      Text(
        phrase,
        style = style,
        modifier =
          Modifier.then(
              if (isActive) Modifier.border(2.dp, colour, shape)
              else Modifier
            )
            .background(
              when {
                isActive -> look.activeChip
                else -> look.chip
              },
              shape,
            )
            .underline(colour, look.underline)
            .clickable { onTap(index) }
            .padding(horizontal = look.padding, vertical = 2.dp),
      )
    }
  }
}

/**
 * The whole translation in its own word order, each chunk underlined in its source phrase's
 * colour and the explored phrase's chunks emphasised. [onTap], when given, explores the first
 * source phrase a chunk translates. [separator] joins the chunks (see `ChunkSeparator`).
 */
@Composable
fun TranslationContext(
  alignment: PhraseAlignment,
  active: Int?,
  style: TextStyle,
  modifier: Modifier = Modifier,
  centred: Boolean = false,
  separator: String = " ",
  onTap: ((Int) -> Unit)? = null,
) {
  PhraseParagraph(
    alignment.translationContext(active).map { chunk ->
      PhraseSpan(
        chunk.text,
        phraseColor(chunk.link),
        underline = if (chunk.active) 3.dp else 1.dp,
        active = chunk.active,
        tap = chunk.sourcePhrase.takeIf { onTap != null },
      )
    },
    style,
    onTap = { onTap?.invoke(it) },
    modifier = modifier,
    centred = centred,
    separator = separator,
  )
}

/**
 * A source line as one paragraph of phrases, for cards where phrases may wrap across lines.
 * [separator] joins the phrases (see `ChunkSeparator`).
 */
@Composable
fun PhraseParagraphLine(
  alignment: PhraseAlignment,
  active: Int?,
  style: TextStyle,
  onTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
  separator: String = " ",
) {
  PhraseParagraph(
    alignment.source().mapIndexed { index, phrase ->
      PhraseSpan(
        phrase,
        phraseColor(alignment.linkOfSource(index)),
        underline = if (active == index) 3.dp else 2.dp,
        active = active == index,
        tap = index,
      )
    },
    style,
    onTap = onTap,
    modifier = modifier,
    separator = separator,
  )
}

/** One phrase in a [PhraseParagraph]: its underline colour and weight, and the index a tap reports. */
class PhraseSpan(val text: String, val colour: Color, val underline: Dp, val active: Boolean, val tap: Int?)

/**
 * Phrases laid out as a single wrapping paragraph, so a phrase that runs over a line break wraps
 * like prose instead of becoming its own block. Underlines and the active phrase's tint are drawn
 * per text line from the layout, and a tap reports the [PhraseSpan.tap] of the phrase under it.
 */
@Composable
fun PhraseParagraph(
  spans: List<PhraseSpan>,
  style: TextStyle,
  onTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
  centred: Boolean = false,
  separator: String = " ",
) {
  val ranges = mutableListOf<IntRange>()
  val text = buildAnnotatedString {
    spans.forEachIndexed { index, span ->
      if (index > 0) append(separator)
      val start = length
      if (span.active) withStyle(SpanStyle(color = Color.White)) { append(span.text.trim()) }
      else append(span.text.trim())
      ranges += start until length
    }
  }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val boxes = remember(layout, text) { layout?.let { result -> ranges.map { result.lineBoxes(it) } }.orEmpty() }
  val latestSpans by rememberUpdatedState(spans)
  val latestBoxes by rememberUpdatedState(boxes)
  // The tap detector outlives recompositions (pointerInput(Unit)), so it must call the latest
  // onTap: a card reused for another bubble would otherwise update the first bubble's state.
  val latestOnTap by rememberUpdatedState(onTap)
  Text(
    text,
    style = style,
    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
    onTextLayout = { layout = it },
    modifier =
      modifier
        .drawBehind {
          val pad = 2.dp.toPx()
          boxes.forEachIndexed { index, lines ->
            val span = spans.getOrNull(index) ?: return@forEachIndexed
            for (box in lines) {
              if (span.active)
                drawRoundRect(
                  span.colour.copy(alpha = 0.22f),
                  Offset(box.left - pad, box.top),
                  Size(box.width + 2 * pad, box.height),
                  CornerRadius(3.dp.toPx()),
                )
              val stroke = span.underline.toPx()
              val y = box.bottom - stroke / 2
              drawLine(span.colour, Offset(box.left, y), Offset(box.right, y), stroke)
            }
          }
        }
        .pointerInput(Unit) {
          detectTapGestures { position ->
            val slop = 4.dp.toPx()
            val hit =
              latestBoxes.indexOfFirst { lines ->
                lines.any { position.x in it.left - slop..it.right + slop && position.y in it.top - slop..it.bottom + slop }
              }
            latestSpans.getOrNull(hit)?.tap?.let(latestOnTap)
          }
        },
  )
}

/**
 * The part of [range] on each text line, from the first glyph's left to the last glyph's right,
 * spanning the line's ascent to just under its descent, where the underline is drawn.
 */
private fun TextLayoutResult.lineBoxes(range: IntRange): List<Rect> {
  if (range.isEmpty()) return emptyList()
  return (getLineForOffset(range.first)..getLineForOffset(range.last)).mapNotNull { line ->
    val start = maxOf(range.first, getLineStart(line))
    var end = minOf(range.last, getLineEnd(line, visibleEnd = true) - 1)
    while (end > start && layoutInput.text[end].isWhitespace()) end--
    if (end < start) return@mapNotNull null
    val first = getBoundingBox(start)
    val last = getBoundingBox(end)
    val baseline = getLineBaseline(line)
    val bottom = minOf(baseline + (getLineBottom(line) - baseline) * 0.6f + 2f, getLineBottom(line))
    Rect(minOf(first.left, last.left), first.top, maxOf(first.right, last.right), bottom)
  }
}

private fun Modifier.underline(colour: Color, width: Dp) = drawBehind {
  val stroke = width.toPx()
  drawLine(colour, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}

/** A learner note with backtick-marked source expressions highlighted. */
@Composable
fun LearnerNote(note: String, style: TextStyle, modifier: Modifier = Modifier) {
  Text(noteText(note), style = style, modifier = modifier)
}

private val sourceExpression =
  SpanStyle(fontWeight = FontWeight.SemiBold, background = Color(0x33E8B97E), color = Color(0xFFF2D7A8))

fun noteText(note: String): AnnotatedString = buildAnnotatedString {
  for (segment in noteSegments(note))
    if (segment.highlighted) withStyle(sourceExpression) { append(segment.text) }
    else append(segment.text)
}
