package com.petitesroues.android.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TitlePathsTest {
  @Test
  fun aTitlesPathIsItsDocumentFolderOrItsDocumentWithoutExtension() {
    assertEquals(
      "study/series/peppa/s01e01",
      TitlePaths.of("study", "series/peppa/s01e01/document.json"),
    )
    assertEquals("study", TitlePaths.of("study", "document.json"))
    assertEquals("study/films/ponyo", TitlePaths.of("study", "films/ponyo.json"))
  }
}
