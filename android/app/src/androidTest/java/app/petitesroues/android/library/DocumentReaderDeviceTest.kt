package app.petitesroues.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.documents.StudyDocumentCodec
import app.petitesroues.documents.StudyDocumentReader
import app.petitesroues.model.document.ComicDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads documents on a real device runtime. JVM tests cannot catch what ART does differently: it
 * does not report the document model's records as records, so every document read failed ("No
 * document" for every title) until the wire root named its constructor for Jackson.
 */
@RunWith(AndroidJUnit4::class)
class DocumentReaderDeviceTest {
  private val reader = StudyDocumentReader(StudyDocumentCodec())

  private val video =
    """
    {"id": "v", "title": "Example", "sourceLanguage": "fr", "translationLanguage": "en",
     "notesLanguage": "en",
     "video": {"cues": [{"start": 0, "end": 2,
       "line": {"id": "l1", "source": "Bonjour !", "notes": []}}]}}
    """
      .trimIndent()

  private val comic =
    """
    {"id": "c", "title": "Tome", "sourceLanguage": "fr", "translationLanguage": "en",
     "notesLanguage": "en",
     "comic": {"pages": [{"id": "p1", "entry": "001.jpg", "boxes": [{"id": "b1",
       "regions": [{"x": 0.1, "y": 0.1, "width": 0.3, "height": 0.2}],
       "lines": [{"id": "l1", "source": "Bonjour !", "notes": []}]}]}]}}
    """
      .trimIndent()

  @Test
  fun readsAVideoDocument() {
    val document = reader.decode(video.toByteArray())
    assertEquals("Example", document.title())
    assertEquals("Bonjour !", document.video().cues().single().line().source())
  }

  @Test
  fun readsAComicDocument() {
    val document = reader.decode(comic.toByteArray())
    assertTrue(document.body() is ComicDocument)
    assertEquals("001.jpg", (document.body() as ComicDocument).pages().single().entry())
  }
}
