package app.petitesroues.android.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.petitesroues.android.library.Title
import app.petitesroues.android.library.TitleKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndexDaoTest {
  private val database =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        IndexDatabase::class.java,
      )
      .allowMainThreadQueries()
      .build()
  private val index = database.index()

  @After fun close() = database.close()

  private fun title(path: String, documentId: String, document: Boolean = true) =
    Title(
      path = path,
      name = documentId,
      series = "Naruto",
      kind = TitleKind.COMIC,
      documentUri = if (document) Uri.parse("content://tree/$path.json") else null,
      mediaUri = Uri.parse("content://tree/$path.cbz"),
      documentId = documentId,
      durationSeconds = null,
      season = null,
      episode = null,
      volume = 1,
      poster = null,
      seriesPoster = Uri.parse("content://tree/poster.jpg"),
      firstPage = "001.jpg",
      shelf = "manga",
      sourceLanguage = "fr",
      mediaPath = "$path.cbz",
    )

  @Test
  fun everyFieldRoundTripsAndAReplaceReplaces() {
    val first =
      listOf(
        title("b/naruto-t02", "naruto-t02"),
        title("a/naruto-t01", "naruto-t01", document = false),
      )
    index.replaceTitles(first)
    assertEquals(first.sortedBy { it.path }, index.titles())
    val second = listOf(title("c/naruto-t03", "naruto-t03"))
    index.replaceTitles(second)
    assertEquals(second, index.titles())
  }

  @Test
  fun fingerprintsAreReplacedWhole() {
    index.replaceFingerprints(
      listOf(
        CachedFingerprint("content://a", 1, 2, "v1:a"),
        CachedFingerprint("content://b", 3, 4, "v1:b"),
      )
    )
    index.replaceFingerprints(listOf(CachedFingerprint("content://b", 5, 6, "v1:b2")))
    assertEquals(
      mapOf("content://b" to CachedFingerprint("content://b", 5, 6, "v1:b2")),
      index.fingerprints(),
    )
  }
}
