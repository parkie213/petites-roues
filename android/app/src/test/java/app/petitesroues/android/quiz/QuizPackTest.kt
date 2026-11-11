package app.petitesroues.android.quiz

import app.petitesroues.android.cue
import app.petitesroues.android.weather
import app.petitesroues.model.document.ComicBox
import app.petitesroues.model.document.ComicChapter
import app.petitesroues.model.document.ComicDocument
import app.petitesroues.model.document.ComicPage
import app.petitesroues.model.document.ComicRegion
import app.petitesroues.model.document.Line
import app.petitesroues.model.document.ReadingDirection
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.document.VideoCue
import app.petitesroues.model.document.VideoDocument
import app.petitesroues.model.pages.MarkdownPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizPackTest {
  private fun at(start: Double, line: Line) = VideoCue(line, start, start + 2, null)

  private fun document(cues: List<VideoCue>) =
    StudyDocument(
      "film",
      "Le Film",
      "fr",
      "en",
      "en",
      null,
      null,
      VideoDocument(null, null, null, cues),
    )

  private fun build(cues: List<VideoCue>, current: Int) =
    QuizPack.build("# Subtitle quiz agent\n\nRules.", document(cues), cues, current)

  @Test
  fun carriesTheRulesAndWatchedLinesWithTheirTranslations() {
    val cues =
      listOf(
        at(
          10.0,
          Line(
            "a",
            "Il fait beau",
            "The weather is nice",
            listOf("Weather uses faire."),
            weather(),
            false,
            null,
          ),
        ),
        at(70.0, cue("b", "Pas encore vu", "Not seen yet")),
      )
    val pack = build(cues, current = 0)
    assertTrue(pack.startsWith("# Subtitle quiz: Le Film"))
    assertTrue("bundled headings sit under the brief's sections", "### Subtitle quiz agent" in pack)
    assertFalse("the teaching guide stays out", "## Teaching guide" in pack)
    assertTrue("`fr`" in pack)
    assertTrue("## Lines just watched (0:10–0:12)" in pack)
    assertTrue("\n- 0:10 Il fait beau → The weather is nice\n" in pack)
    assertFalse(
      "learner notes are left out",
      "adjective describes" in pack || "Weather uses faire" in pack,
    )
    assertFalse("lines after the current one are not included", "Pas encore vu" in pack)
  }

  @Test
  fun keepsOnlyTheLastLinesWithSourceText() {
    val cues =
      (0 until 40).map { at(it * 3.0, cue("c$it", if (it == 39) "" else "Ligne $it", "Line $it")) }
    val pack = build(cues, current = 39)
    assertEquals(
      QuizPack.LINES,
      Regex("^- \\d+:\\d\\d ", RegexOption.MULTILINE).findAll(pack).count(),
    )
    assertTrue("Ligne 38 → Line 38" in pack)
    assertFalse("Ligne 8 " in pack)
  }

  @Test
  fun aChapterBriefCarriesItsBubblesInReadingOrder() {
    val bubble = { line: Line ->
      ComicBox("box-${line.id()}", listOf(ComicRegion(0.1, 0.1, 0.2, 0.2)), listOf(line))
    }
    val page = { id: String, lines: List<Line> ->
      ComicPage(id, "$id.png", null, lines.map(bubble))
    }
    val pages =
      listOf(
        page("cover", listOf(cue("t", "Demon Slayer"))),
        page("a1", listOf(cue("c", "C'est un cauchemar !"))),
        page(
          "a2",
          listOf(
            Line("n", "Nezuko…", "", listOf("A name called out."), null, false, null),
            cue("w", "Je te sauverai !", "I'll save you!"),
          ),
        ),
        page("b1", listOf(cue("e", "Excusez-moi…"))),
      )
    val comic =
      ComicDocument(
        null,
        ReadingDirection.RTL,
        null,
        pages,
        listOf(ComicChapter(1, "Cruauté", "a1"), ComicChapter(2, "Inconnu", "b1")),
      )
    val document = StudyDocument("t1", "Demon Slayer — Tome 1", "fr", "en", "en", null, null, comic)
    val pack = QuizPack.chapter("# Subtitle quiz agent\n\nRules.", document, comic, 0)
    assertTrue(pack.startsWith("# Chapter quiz: Demon Slayer — Tome 1, chapter 1 · Cruauté\n"))
    assertTrue(
      "## Bubbles in chapter 1 · Cruauté (pages 2–3)\n\nPick items from all over these lines, in a mixed order" in
        pack
    )
    assertTrue(
      "### Page 2\n\n- C'est un cauchemar !\n\n### Page 3\n\n- Nezuko…\n- Je te sauverai ! → I'll save you!\n" in
        pack
    )
    assertFalse("A name called out" in pack || "Excusez-moi" in pack || "- Demon Slayer" in pack)
    assertEquals("quiz-demon-slayer-tome-1-chapter-2.md", QuizPack.fileName(document, "chapter 2"))
  }

  @Test
  fun saysSoBeforeTheFirstLine() {
    assertTrue("None yet" in build(listOf(at(5.0, cue("a", "Bonjour"))), current = -1))
  }

  private val types = java.io.File("../../agents/workflows/quiz-types.md").readText()

  @Test
  fun everyQuizTypeHasInstructionsInQuizTypesMd() {
    val document = document(emptyList())
    for (type in QuizPack.TYPES) {
      val chosen = QuizPack.type(types, type.id, document)
      assertTrue(type.id, chosen.instructions.length > 40)
      assertFalse("${type.id} stops at the next type", "## " in chosen.instructions)
    }
    assertEquals("Translate into French", QuizPack.type(types, "to-source", document).label)
    assertEquals("Translate into English", QuizPack.type(types, "to-translation", document).label)
    assertEquals("to-source", QuizPack.type(types, "unknown", document).id)
  }

  @Test
  fun theChosenTypeSitsAboveTheRules() {
    val cues = listOf(at(1.0, cue("a", "Il est parti")))
    val pack =
      QuizPack.build(
        "# Rules",
        document(cues),
        cues,
        0,
        QuizPack.type(types, "tenses", document(cues)),
      )
    val at = pack.indexOf("## Quiz type: Verb tenses")
    assertTrue(at > 0 && at < pack.indexOf("## Quiz rules"))
    assertTrue("Ask for the tense or mood and the infinitive" in pack)
  }

  @Test
  fun aGeneralBriefCarriesTheQuizThenTheRulesWithHeadingsNested() {
    val quiz =
      MarkdownPage(
        "primary:q",
        "french-quizzes",
        "quizzes/common-irregular-verbs",
        "Common irregular verbs",
        "# Common irregular verbs\n\nDrill être.\n\n## Traps\n",
      )
    val brief = QuizPack.general("# Subtitle quiz agent\n\nOne item at a time.", quiz)
    assertTrue(brief.startsWith("# Quiz: Common irregular verbs\n"))
    assertTrue(brief.contains("make up natural, varied sentences"))
    assertTrue(
      brief.contains("## The quiz\n\n### Common irregular verbs\n\nDrill être.\n\n#### Traps")
    )
    assertTrue(brief.indexOf("## The quiz") < brief.indexOf("## Quiz rules"))
    assertTrue(brief.endsWith("### Subtitle quiz agent\n\nOne item at a time.\n"))
    assertEquals("quiz-common-irregular-verbs.md", QuizPack.fileName(quiz))
  }
}
