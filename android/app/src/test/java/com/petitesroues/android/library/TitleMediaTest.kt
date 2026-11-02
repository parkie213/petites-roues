package com.petitesroues.android.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TitleMediaTest {
  @Test
  fun videoTitlesUseTheirSingleVideo() {
    assertEquals("Ep 1.MKV", TitleMedia.select(listOf("notes.txt", "Ep 1.MKV", "book.cbz"), TitleKind.VIDEO))
    assertNull(TitleMedia.select(listOf("book.cbz"), TitleKind.VIDEO))
  }

  @Test
  fun comicTitlesUseTheirSingleCbz() {
    assertEquals("book.cbz", TitleMedia.select(listOf("video.mp4", "book.cbz"), TitleKind.COMIC))
  }

  @Test
  fun ambiguousMediaIsAProblem() {
    assertThrows(IllegalArgumentException::class.java) {
      TitleMedia.select(listOf("a.mp4", "b.webm"), TitleKind.VIDEO)
    }
  }
}
