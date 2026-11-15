package app.petitesroues.android.quiz

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.documents.StudyDocumentCodec
import app.petitesroues.documents.StudyDocumentReader
import app.petitesroues.quiz.QuizGuides
import app.petitesroues.quiz.QuizTime
import app.petitesroues.quiz.QuizType
import app.petitesroues.quiz.VideoQuizBuilder
import app.petitesroues.quiz.VideoQuizScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared video quiz on a real device runtime: the Java builder, scopes and bundled guides the
 * phone's Quiz me dialog relies on. JVM tests cannot show what ART does with a decoded document or
 * whether the guides packaged as jar resources reach the APK.
 */
@RunWith(AndroidJUnit4::class)
class VideoQuizDeviceTest {
  private val document =
    StudyDocumentReader(StudyDocumentCodec())
      .decode(
        """
        {"id": "v", "title": "Le Film", "sourceLanguage": "fr", "translationLanguage": "de",
         "notesLanguage": "en", "shelf": "Films",
         "video": {"reference": {"durationSeconds": 3600}, "cues": [
           {"start": 10, "end": 12, "line": {"id": "a", "source": "Il fait beau",
             "translation": "Das Wetter ist schoen", "notes": ["Weather uses faire."]}},
           {"start": 700, "end": 702, "line": {"id": "b", "source": "A bientot",
             "translation": "Bis bald", "notes": []}}]}}
        """
          .trimIndent()
          .toByteArray()
      )

  private val guides = QuizGuides.bundled()

  @Test
  fun buildsWholeAndRangeBriefsFromADecodedDocument() {
    val whole = VideoQuizBuilder.build(guides, document, VideoQuizScope.Whole(), QuizType.TENSES)
    assertTrue(whole.text().startsWith("# Subtitle quiz: Le Film"))
    assertTrue("## Whole title" in whole.text())
    assertTrue("Il fait beau → Das Wetter ist schoen" in whole.text())
    assertFalse("learner notes stay out", "Weather uses faire" in whole.text())
    assertEquals(2, whole.lineCount())
    assertFalse(whole.large())

    val range =
      VideoQuizBuilder.build(guides, document, VideoQuizScope.TimeRange(600.0, 720.0), null)
    assertTrue("A bientot" in range.text() && "Il fait beau" !in range.text())
    assertEquals(1, range.lineCount())
    assertTrue(range.fileName().startsWith("quiz-le-film"))
  }

  @Test
  fun opensOnTheDefaultAndCountsTheScope() {
    val form = VideoQuizScope.defaultFor(document, 720.0, 3600.0)
    assertEquals(VideoQuizScope.Kind.RANGE, form.kind())
    assertEquals("2:00", form.start())
    assertEquals("12:00", form.end())
    val scope = VideoQuizScope.of(form).orElseThrow()
    assertEquals(1, VideoQuizBuilder.count(document, scope))
    assertTrue(
      VideoQuizScope.of(VideoQuizScope.Form(VideoQuizScope.Kind.RANGE, "9:00", "1:00")).isEmpty
    )
  }

  @Test
  fun namesQuizTypesAndRoundTripsTimes() {
    assertEquals("Translate into French", QuizType.TO_SOURCE.label(document))
    assertEquals("Translate into German", QuizType.TO_TRANSLATION.label(document))
    assertEquals(QuizType.TO_SOURCE, QuizType.of("unknown"))
    assertEquals(3725.0, QuizTime.parse("1:02:05").asDouble, 0.0)
    assertEquals("1:02:05", QuizTime.format(3725.4))
    assertFalse(QuizTime.parse("12.5").isPresent)
  }

  @Test
  fun bundlesTheQuizGuidesInTheApk() {
    assertTrue(guides.rules().isNotBlank())
    assertTrue("# Subtitle quiz" in guides.rules())
    for (type in QuizType.entries) assertTrue(
      type.id(),
      type.chosen(guides.types(), document).instructions().length > 40,
    )
  }
}
