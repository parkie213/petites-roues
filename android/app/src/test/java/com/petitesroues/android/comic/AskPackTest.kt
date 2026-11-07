package com.petitesroues.android.comic

import com.petitesroues.android.cue
import com.petitesroues.android.weather
import com.petitesroues.model.document.ComicBox
import com.petitesroues.model.document.ComicDocument
import com.petitesroues.model.document.ComicPage
import com.petitesroues.model.document.ReadingDirection
import com.petitesroues.model.document.Line
import com.petitesroues.model.document.StudyDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskPackTest {
  private fun bubble(line: Line) = ComicBox("box-${line.id()}", listOf(), listOf(line))

  private val beau =
    bubble(Line("a", "Il fait beau", "The weather is nice", listOf("Weather uses faire."), weather(), false, null))
  private val other = bubble(cue("b", "On sort ?", "Shall we go out?"))
  private val pending = bubble(cue("c"))

  private fun document(boxes: List<ComicBox>) =
    StudyDocument("comic", "Le Livre", "fr", "en", "en", null, null, null, ComicDocument(null, ReadingDirection.RTL, null, listOf(ComicPage("p1", "001.png", "12", boxes)), listOf()))

  @Test
  fun carriesTheQuestionBubbleNotesAndPageContext() {
    val cues = listOf(beau, other, pending)
    val message = AskPack.build(document(cues), "12", beau.lines()[0], cues, phrase = 0, question = "  Why faire?  ")
    assertTrue(message.startsWith("I'm learning French by reading “Le Livre”"))
    assertTrue("page 12" in message)
    assertTrue("My question: Why faire?\n" in message)
    assertTrue("I'm asking about the phrase “Il fait”." in message)
    assertTrue("Bubble: Il fait beau" in message)
    assertTrue("Translation: The weather is nice" in message)
    assertTrue("- “Il fait” → The weather is: `il fait` + adjective describes the weather" in message)
    assertTrue("- Weather uses faire." in message)
    assertTrue("→ Il fait beau (The weather is nice)" in message)
    assertTrue("- On sort ? (Shall we go out?)" in message)
    assertTrue(message.endsWith("Keep it concise."))
    assertTrue("Please answer in English." in message)
  }

  @Test
  fun aBlankQuestionAsksForMoreDepthAndALoneBubbleHasNoContextList() {
    val cues = listOf(other)
    val message = AskPack.build(document(cues), "3", other.lines()[0], cues, phrase = null, question = " ")
    assertTrue("Please explain this bubble in more depth." in message)
    assertFalse("My question" in message)
    assertFalse("asking about the phrase" in message)
    assertFalse("for context" in message)
    assertEquals(1, Regex("Bubble: ").findAll(message).count())
  }

  @Test
  fun aTextBlockSentenceIsAskedAboutWithItsSentencesAsContext() {
    val first = cue("s1", "Jeune sœur de Tanjiro.", "Tanjiro's younger sister.")
    val second = cue("s2", "Elle a été transformée en démon.", "She was turned into a demon.")
    val block = ComicBox("intro", listOf(), listOf(first, second))
    val cues = listOf(other, block)
    val message = AskPack.build(document(cues), "4", second, cues, phrase = null, question = "")
    assertTrue("Bubble: Elle a été transformée en démon." in message)
    assertTrue("- On sort ? (Shall we go out?)" in message)
    assertTrue("- Jeune sœur de Tanjiro. (Tanjiro's younger sister.)" in message)
    assertTrue("→ Elle a été transformée en démon. (She was turned into a demon.)" in message)
  }
}
