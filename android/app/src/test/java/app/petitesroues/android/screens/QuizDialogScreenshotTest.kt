package app.petitesroues.android.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.android.AppTheme
import app.petitesroues.android.quiz.QuizChoices
import app.petitesroues.android.quiz.QuizForm
import app.petitesroues.android.quiz.VideoQuiz
import app.petitesroues.model.document.VideoCue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The video Quiz me dialog on the example Peppa Pig episode: a short title opens on the whole
 * title, a long one (the episode's lines repeated to fill two hours) on the last ten minutes
 * watched, and a scope of more than 400 lines shows the size warning.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [SDK], qualifiers = PHONE)
class QuizDialogScreenshotTest {
  @get:Rule val compose = createComposeRule()

  private val document = Examples.document("series/peppa-pig/s01e01")

  /** The example's cues repeated end to end until they run for [minutes]. */
  private fun longCues(minutes: Int): List<VideoCue> {
    val once = document.video().cues()
    val length = once.maxOf { it.end() } + 1
    return (0 until (minutes * 60 / length).toInt() + 1).flatMap { copy ->
      once.map { VideoCue(it.line(), it.start() + copy * length, it.end() + copy * length, null) }
    }
  }

  private fun show(video: VideoQuiz, choose: String? = null) {
    // Robolectric never settles a text field inside a dialog window (its cursor animation keeps
    // Compose busy), so the dialog's content is drawn on the dialog's own surface instead.
    val choices = QuizChoices(document, "to-source", 0, 0, video)
    compose.setContent {
      AppTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
          Column(Modifier.padding(24.dp)) {
            Text("Quiz me", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            QuizForm(document, emptyList(), choices)
          }
        }
      }
    }
    if (choose != null) compose.onNode(hasText(choose) and hasClickAction()).performClick()
    compose.onRoot().captureRoboImage()
  }

  @Test
  fun shortTitle() {
    val cues = document.video().cues()
    show(VideoQuiz(cues, 62.0, 30.0, 4))
  }

  @Test
  fun longFilmOpensOnTheLastTenMinutes() {
    val cues = longCues(120)
    show(VideoQuiz(cues, 7200.0, 3723.0, cues.indexOfLast { it.start() <= 3723.0 }))
  }

  @Test
  fun tooManyLinesWarns() {
    val cues = longCues(120)
    show(VideoQuiz(cues, 7200.0, 3723.0, 100), choose = "Whole title")
    compose.onNodeWithText("more than 400 lines", substring = true).assertExists()
  }
}
