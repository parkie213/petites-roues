package app.petitesroues.android.quiz

import app.petitesroues.android.cue
import app.petitesroues.model.document.ComicBox
import app.petitesroues.model.document.ComicChapter
import app.petitesroues.model.document.ComicDocument
import app.petitesroues.model.document.ComicPage
import app.petitesroues.model.document.ComicRegion
import app.petitesroues.model.document.Line
import app.petitesroues.model.document.ReadingDirection
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.pages.MarkdownPage
import app.petitesroues.quiz.QuizType
import app.petitesroues.quiz.VideoQuizBuilder.Guides
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizPackTest {
  private val guides =
    Guides(
      "# Subtitle quiz agent\n\nRules.",
      File("../../agents/workflows/quiz-types.md").readText(),
    )

  private fun volume(): StudyDocument {
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
    return StudyDocument("t1", "Demon Slayer — Tome 1", "fr", "en", "en", null, null, comic)
  }

  @Test
  fun aChapterBriefCarriesItsBubblesInReadingOrder() {
    val pack = QuizPack.chapter(guides, volume(), 0)
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
  }

  @Test
  fun aChapterBriefCarriesTheChosenTypeAboveTheRules() {
    val pack = QuizPack.chapter(guides, volume(), 0, QuizType.TENSES)
    val at = pack.indexOf("## Quiz type: Verb tenses")
    assertTrue(at > 0 && at < pack.indexOf("## Quiz rules"))
    assertTrue("Ask for the tense or mood and the infinitive" in pack)
  }

  @Test
  fun aGeneralBriefCarriesTheQuizThenTheRulesWithHeadingsNested() {
    val quiz =
      MarkdownPage(
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
