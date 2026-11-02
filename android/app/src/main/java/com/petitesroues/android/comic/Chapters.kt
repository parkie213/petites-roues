package com.petitesroues.android.comic

import com.petitesroues.model.document.ComicChapter
import com.petitesroues.model.document.ComicPage

/** Which chapter a page belongs to, and how the reader names chapters. */
object Chapters {
  /**
   * Position in [chapters] of the chapter containing page [index], or -1 before the first one. A
   * chapter runs from its first page until the next chapter starts.
   */
  fun at(pages: List<ComicPage>, chapters: List<ComicChapter>, index: Int): Int =
    chapters.indexOfLast { chapter ->
      val start = pages.indexOfFirst { it.id() == chapter.pageId() }
      start in 0..index
    }

  /** "2 · Inconnu", or just the title for an unnumbered section. */
  fun name(chapter: ComicChapter): String =
    chapter.number()?.let { "$it · ${chapter.title()}" } ?: chapter.title()

  /** "Ch. 2" for the page bar, or the title of an unnumbered section. */
  fun short(chapter: ComicChapter): String = chapter.number()?.let { "Ch. $it" } ?: chapter.title()
}
