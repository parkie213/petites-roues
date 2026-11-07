package com.petitesroues.android.study

import com.petitesroues.android.cue
import com.petitesroues.android.weather
import com.petitesroues.model.document.Line
import com.petitesroues.model.document.PhraseAlignment
import com.petitesroues.model.document.PhraseLink
import com.petitesroues.model.document.StudyDocument
import com.petitesroues.model.document.VideoDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private fun document(source: String) =
  StudyDocument("d", "D", source, "en", "en", null, null, VideoDocument(null, null, null, listOf()))

private val FRENCH = document("fr")

class AlignmentTest {
  @Test
  fun readyOnlyWhileTheAlignmentMatchesItsCue() {
    val alignment = weather()
    assertSame(alignment, readyAlignment(cue("a", "Il fait beau", "The weather is nice", alignment), FRENCH))
    assertNull(readyAlignment(cue("a", "Il fait froid", "The weather is nice", alignment), FRENCH))
    assertNull(readyAlignment(cue("a", "Il fait beau", "The weather is nice"), FRENCH))
    // Untranslated lines have a null translation; they show as plain text, never crash.
    assertNull(readyAlignment(Line("a", "Il fait beau", null, listOf(), alignment, false, null), FRENCH))
  }

  @Test
  fun chineseChunksJoinWithoutSpaces() {
    val alignment =
      PhraseAlignment(
        listOf("我们", "去", "公园。"),
        listOf("We", "go", "to the park."),
        listOf(PhraseLink(listOf(0, 1, 2), listOf(0, 1, 2), null)),
      )
    val line = cue("a", "我们去公园。", "We go to the park.", alignment)
    assertSame(alignment, readyAlignment(line, document("zh-Hans")))
    assertNull(readyAlignment(line, FRENCH))
  }

  @Test
  fun translationContextMarksTheExploredPhrasesChunks() {
    val chunks = weather().translationContext(1)
    assertEquals(listOf("The weather is", "nice"), chunks.map { it.text })
    assertEquals(listOf(false, true), chunks.map { it.active })
    assertEquals(listOf(0, 1), chunks.map { it.link })
    assertEquals(listOf(0, 1), chunks.map { it.sourcePhrase })
    assertEquals(listOf(false, false), weather().translationContext(null).map { it.active })
  }

  @Test
  fun phraseNotesAreDistinct() {
    assertEquals(listOf("`beau` here means fine weather"), weather().phraseNotes(1))
    assertEquals(0, weather().linkOfSource(0))
  }

  @Test
  fun noteSegmentsHighlightBacktickSpansOnly() {
    assertEquals(
      listOf(
        NoteSegment("Use ", false),
        NoteSegment("avoir envie de", true),
        NoteSegment(" here; ` stays literal", false),
      ),
      noteSegments("Use `avoir envie de` here; ` stays literal"),
    )
    assertEquals(listOf(NoteSegment("``", false)), noteSegments("``"))
  }
}
