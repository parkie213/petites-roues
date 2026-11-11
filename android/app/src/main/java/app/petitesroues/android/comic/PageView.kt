package app.petitesroues.android.comic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.petitesroues.model.document.ComicBox
import app.petitesroues.model.document.ComicPage
import kotlin.math.abs

/**
 * One page image, fitted to its slot against [alignment] (the spine in a spread), with its bubbles
 * as tap targets in normalised coordinates.
 */
@Composable
internal fun PageView(
  load: suspend (entry: String, maxWidth: Int, maxHeight: Int) -> Result<ImageBitmap>,
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
        value = load(page.entry(), slot.first, slot.second)
      }
    val bitmap = image?.getOrNull()
    when {
      image == null -> CircularProgressIndicator()
      bitmap == null ->
        Text(image!!.exceptionOrNull()?.message ?: "Cannot show page", color = Color(0xFFFFB4A9))
      else ->
        BoxWithConstraints(Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
          Image(
            bitmap,
            contentDescription = page.label(),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
          boxes.forEachIndexed { number, box ->
            val isSelected = box.id() == highlighted?.id()
            box.regions().forEachIndexed { r, region ->
              Box(
                Modifier.offset(maxWidth * region.x().toFloat(), maxHeight * region.y().toFloat())
                  .size(maxWidth * region.width().toFloat(), maxHeight * region.height().toFloat())
                  .then(
                    // As in the browser: the open bubble gets only a faint warm wash so its art
                    // stays readable; a border marks it only while outlines are shown.
                    when {
                      isSelected && outlined ->
                        Modifier.border(2.dp, Color(0xFFFFCE72), RoundedCornerShape(6.dp))
                          .background(Color(0x1AFFC45D), RoundedCornerShape(6.dp))
                      isSelected -> Modifier.background(Color(0x24FFC45D), RoundedCornerShape(6.dp))
                      outlined -> Modifier.border(1.dp, Color(0xC067C5FF), RoundedCornerShape(4.dp))
                      else -> Modifier
                    }
                  )
                  .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) {
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
 * Keeps gestures that travel more vertically than horizontally away from the pager, so the swipe up
 * for the system bars or the app switcher does not turn the page when it drifts sideways: the pager
 * would otherwise start dragging after a few pixels of sideways movement, however steep the swipe.
 * Taps and mostly horizontal swipes pass through untouched.
 */
internal fun Modifier.keepVerticalSwipesFromPager(): Modifier =
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
