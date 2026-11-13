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
      assertThrows(IllegalStateException::class.java) { it.page("absent.png", 8, 8) }
      assertThrows(IllegalArgumentException::class.java) { it.page("folder.jpg/", 8, 8) }
    }
  }

  @Test
  fun `rejects duplicate file names on opening`() {
    val file = archive {
      entry(it, "page.png")
      entry(it, "page.png")
    }
    assertThrows(IllegalArgumentException::class.java) { open(file).use {} }
  }

  @Test
  fun `rejects archives with too many entries`() {
    val file = archive { zip -> repeat(20001) { entry(zip, "$it.txt") } }
    assertThrows(IllegalArgumentException::class.java) { open(file).use {} }
  }

  @Test
  fun `rejects oversized decompressed pages before image decoding`() {
    val file = archive { zip ->
      zip.putArchiveEntry(ZipArchiveEntry("huge.png"))
      val block = ByteArray(1024 * 1024)
      repeat(33) { zip.write(block) }
      zip.closeArchiveEntry()
    }
    open(file).use {
      val failure =
        assertThrows(IllegalArgumentException::class.java) {
          it.page("huge.png", 8, 8)
        }
      assertEquals("CBZ page exceeds 32 MiB", failure.message)
    }
  }

  @Test
  fun `bounds actual decompression when the ZIP understates page size`() {
    val file = archive { zip ->
      zip.putArchiveEntry(ZipArchiveEntry("huge.png"))
      val block = ByteArray(1024 * 1024)
      repeat(33) { zip.write(block) }
      zip.closeArchiveEntry()
    }
    // The central directory claims one byte, while the deflate stream produces 33 MiB.
    val bytes = file.readBytes()
    val central =
      (0 until bytes.size - 28).first { offset ->
        bytes[offset] == 0x50.toByte() &&
          bytes[offset + 1] == 0x4b.toByte() &&
          bytes[offset + 2] == 1.toByte() &&
          bytes[offset + 3] == 2.toByte()
      }
    bytes[central + 24] = 1
    for (index in 25..27) bytes[central + index] = 0
    file.writeBytes(bytes)
    open(file).use {
      val failure =
        assertThrows(IllegalArgumentException::class.java) {
          it.page("huge.png", 8, 8)
        }
      assertEquals("CBZ page exceeds 32 MiB", failure.message)
    }
  }

  @Test
  fun `rejects malformed archives on opening`() {
    val file = folder.newFile("broken.cbz").apply { writeText("not a zip") }
    assertThrows(java.io.IOException::class.java) { open(file).use {} }
  }
}
