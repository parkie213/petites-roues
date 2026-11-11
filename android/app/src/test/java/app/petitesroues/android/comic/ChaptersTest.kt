package app.petitesroues.android.comic

import app.petitesroues.model.document.ComicChapter
import app.petitesroues.model.document.ComicPage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChaptersTest {
  private val pages =
    listOf("cover", "toc", "a1", "a2", "b1", "bonus").map { ComicPage(it, "$it.png", null) }
  private val chapters =
    listOf(
      ComicChapter(1, "Cruauté", "a1"),
      ComicChapter(2, "Inconnu", "b1"),
      ComicChapter(null, "Bonus", "bonus"),
    )

  @Test
  fun aChapterRunsUntilTheNextOneStarts() {
    assertEquals(listOf(-1, -1, 0, 0, 1, 2), pages.indices.map { Chapters.at(pages, chapters, it) })
    assertEquals(-1, Chapters.at(pages, emptyList(), 3))
  }

  @Test
  fun unnumberedSectionsAreNamedByTitle() {
    assertEquals("2 · Inconnu", Chapters.name(chapters[1]))
    assertEquals("Ch. 2", Chapters.short(chapters[1]))
    assertEquals("Bonus", Chapters.name(chapters[2]))
    assertEquals("Bonus", Chapters.short(chapters[2]))
  }
}
