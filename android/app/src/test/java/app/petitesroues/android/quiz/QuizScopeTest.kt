package app.petitesroues.android.quiz

import app.petitesroues.android.cue
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.document.VideoCue
import app.petitesroues.model.document.VideoDocument
import app.petitesroues.model.document.VideoReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScopeTest {
  /** [count] two-second cues, one every [step] seconds. */
  private fun cues(count: Int, step: Double = 3.0) =
    (0 until count).map {
      VideoCue(cue("c$it", "Ligne $it", "Line $it"), it * step, it * step + 2, null)
    }

  private fun document(cues: List<VideoCue>, shelf: String? = null, duration: Double? = null) =
    StudyDocument(
      "film",
      "Le Film",
      "fr",
      "en",
      "en",
      null,
      shelf,
      null,
      VideoDocument(null, null, duration?.let { VideoReference("f", null, it) }, cues),
    )

  @Test
  fun parsesTimesLikeTheBrowser() {
    assertEquals(0.0, QuizScope.parseTime("0:00")!!, 0.0)
    assertEquals(75.0, QuizScope.parseTime("1:15")!!, 0.0)
    assertEquals(3723.0, QuizScope.parseTime("1:02:03")!!, 0.0)
    assertEquals(630.0, QuizScope.parseTime("10:30")!!, 0.0)
    for (bad in
      listOf(
        "",
        "5",
        "1:5",
        "1:60",
        "1:61:00",
        "1:02:60",
        "1.5",
        "a:bc",
        "1:02:03:04",
        "-1:00",
      )) assertNull(bad, QuizScope.parseTime(bad))
    assertNull(QuizScope.parseTime("1:15.5"))
  }

  @Test
  fun clockReadsBackThroughParseTime() {
    for (seconds in listOf(0.0, 59.0, 600.0, 3599.0, 3600.0, 7325.0)) assertEquals(
      seconds,
      QuizScope.parseTime(QuizScope.clock(seconds))!!,
      0.0,
    )
  }

  @Test
  fun eachScopeSelectsItsLines() {
    val cues = cues(40)
    assertEquals(40, QuizScope.select(cues, QuizScope.Whole).size)
    val last = QuizScope.select(cues, QuizScope.Last(39))
    assertEquals(QuizScope.LAST_LINES, last.size)
    assertEquals("c39", last.last().line().id())
    assertEquals("c10", last.first().line().id())
    assertTrue(QuizScope.select(cues, QuizScope.Last(-1)).isEmpty())
    // Cue k covers [3k, 3k+2): 10 to 20 overlaps c4 (12-14), c5, c6 (18-20) but not c3 (9-11)...
    // which does overlap, since 11 > 10.
    val range = QuizScope.select(cues, QuizScope.Range(10.0, 20.0)).map { it.line().id() }
    assertEquals(listOf("c3", "c4", "c5", "c6"), range)
    assertTrue(QuizScope.select(cues, QuizScope.Range(500.0, 600.0)).isEmpty())
  }

  @Test
  fun linesWithoutSourceTextAreNeverSelected() {
    val blank = VideoCue(cue("b", "", "x"), 1.0, 2.0, null)
    val cues = listOf(blank) + cues(2)
    for (scope in
      listOf(QuizScope.Whole, QuizScope.Range(0.0, 99.0), QuizScope.Last(2))) assertEquals(
      2,
      QuizScope.select(cues, scope).size,
    )
  }

  @Test
  fun aShortTitleOpensOnTheWholeTitle() {
    val cues = cues(100) // ends at 299 s
    val initial = QuizScope.initial(document(cues), QuizScope.length(document(cues), cues), 120.0)
    assertEquals(QuizScope.Choice.WHOLE, initial.choice)
  }

  @Test
  fun aLongTitleOpensOnTheLastTenMinutesWatched() {
    val cues = cues(1000, 7.0)
    val doc = document(cues, duration = 7200.0)
    assertEquals(7200.0, QuizScope.length(doc, cues), 0.0)
    assertEquals(
      QuizScope.Initial(QuizScope.Choice.RANGE, "52:03", "1:02:03"),
      QuizScope.initial(doc, 7200.0, 3723.0),
    )
    // Near the start the range begins at 0:00 rather than before it.
    assertEquals("0:00", QuizScope.initial(doc, 7200.0, 200.0).start)
    // Not started yet: the first ten minutes.
    assertEquals(
      QuizScope.Initial(QuizScope.Choice.RANGE, "0:00", "10:00"),
      QuizScope.initial(doc, 7200.0, 0.0),
    )
  }

  @Test
  fun lengthFallsBackFromTheDocumentToThePlayerToTheLastCue() {
    val cues = cues(10) // ends at 29 s
    assertEquals(500.0, QuizScope.length(document(cues, duration = 500.0), cues, 90.0), 0.0)
    assertEquals(90.0, QuizScope.length(document(cues), cues, 90.0), 0.0)
    assertEquals(29.0, QuizScope.length(document(cues), cues, 0.0), 0.0)
    assertEquals(0.0, QuizScope.length(document(emptyList()), emptyList()), 0.0)
    // The 15-minute boundary uses that length.
    assertEquals(QuizScope.Choice.RANGE, QuizScope.initial(document(cues), 900.0, 100.0).choice)
    assertEquals(QuizScope.Choice.WHOLE, QuizScope.initial(document(cues), 899.0, 100.0).choice)
  }

  @Test
  fun aNonFilmShelfOpensOnTheWholeTitleWhateverItsLength() {
    val long = cues(10)
    for (shelf in listOf("Music", "Songs")) assertEquals(
      QuizScope.Choice.WHOLE,
      QuizScope.initial(document(long, shelf), 4000.0, 3000.0).choice,
    )
    for (shelf in listOf("Films", null)) assertEquals(
      QuizScope.Choice.RANGE,
      QuizScope.initial(document(long, shelf), 4000.0, 3000.0).choice,
    )
  }

  @Test
  fun theDialogsFieldsMakeAScopeOnlyWhenTheRangeIsValid() {
    assertEquals(QuizScope.Whole, QuizScope.of(QuizScope.Choice.WHOLE, "garbage", "", 3))
    assertEquals(QuizScope.Last(7), QuizScope.of(QuizScope.Choice.LAST, "", "", 7))
    assertEquals(
      QuizScope.Range(60.0, 125.0),
      QuizScope.of(QuizScope.Choice.RANGE, " 1:00", "2:05 ", 0),
    )
    for ((from, to) in
      listOf("1:00" to "1:00", "2:00" to "1:00", "x" to "2:00", "1:00" to "2:7")) assertNull(
      "$from-$to",
      QuizScope.of(QuizScope.Choice.RANGE, from, to, 0),
    )
  }

  @Test
  fun warnsOnlyAboveTheSuggestedCapAndNeverTruncates() {
    assertEquals(400, QuizScope.SUGGESTED_CAP)
    assertFalse(QuizScope.overCap(QuizScope.SUGGESTED_CAP))
    assertTrue(QuizScope.overCap(QuizScope.SUGGESTED_CAP + 1))
    val cues = cues(450)
    val selected = QuizScope.select(cues, QuizScope.Whole)
    assertEquals(450, selected.size)
    assertTrue(QuizScope.overCap(selected.size))
    val brief = QuizPack.build("# Rules", document(cues), cues, QuizScope.Whole)
    assertEquals(450, Regex("^- ", RegexOption.MULTILINE).findAll(brief).count())
  }
}
