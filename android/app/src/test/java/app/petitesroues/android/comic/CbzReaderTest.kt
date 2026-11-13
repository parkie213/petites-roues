package app.petitesroues.android.comic

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.android.screens.SDK
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exercises the phone's actual file-descriptor archive reader with valid and malformed ZIPs. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class CbzReaderTest {
  @get:Rule val folder = TemporaryFolder()
  private val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver

  private fun archive(write: (ZipArchiveOutputStream) -> Unit): File =
    folder.newFile("${System.nanoTime()}.cbz").also { file ->
      ZipArchiveOutputStream(file).use(write)
    }

  private fun entry(zip: ZipArchiveOutputStream, name: String, bytes: ByteArray = byteArrayOf()) {
    zip.putArchiveEntry(ZipArchiveEntry(name))
    zip.write(bytes)
    zip.closeArchiveEntry()
  }

  private fun open(file: File) = CbzReader(resolver, Uri.fromFile(file))

  @Test
  fun `lists images in natural order and decodes a page`() {
    val png =
      ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
      }
    val file = archive {
      entry(it, "page-10.PNG", png)
      entry(it, "page-2.png", png)
      entry(it, "notes.txt")
      entry(it, "folder.jpg/")
    }
    open(file).use {
      assertEquals(listOf("page-2.png", "page-10.PNG"), it.pages())
      assertEquals("page-2.png", it.firstPage())
      assertEquals(8, it.page("page-2.png", 8, 8).width)
      assertThrows(IllegalArgumentException::class.java) { it.page("absent.png", 8, 8) }
      assertThrows(IllegalArgumentException::class.java) { it.page("folder.jpg/", 8, 8) }
    }
  }

  @Test
  fun `rejects malformed archives on opening`() {
    val file = folder.newFile("broken.cbz").apply { writeText("not a zip") }
    assertThrows(java.io.IOException::class.java) { open(file).use {} }
  }
}
