package app.petitesroues.android.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUsageTest {
  @Test
  fun formatsSizesInDecimalUnits() {
    assertEquals("0 B", LibraryUsage.format(0))
    assertEquals("1.5 kB", LibraryUsage.format(1500))
    assertEquals("41.2 GB", LibraryUsage.format(41_234_000_000))
    assertEquals("210 GB", LibraryUsage.format(210_400_000_000))
  }

  @Test
  fun leavesOutTheSizeUntilARefreshHasMeasuredIt() {
    assertEquals(
      "Library 4.1 GB · 18.3 GB free",
      LibraryUsage.line(4_100_000_000, listOf(18_300_000_000)),
    )
    assertEquals("18.3 GB free", LibraryUsage.line(null, listOf(18_300_000_000)))
    assertEquals("", LibraryUsage.line(null, emptyList()))
  }
}
