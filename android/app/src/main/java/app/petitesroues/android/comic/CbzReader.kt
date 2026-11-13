package app.petitesroues.android.comic

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.petitesroues.library.ordering.NaturalOrder
import java.io.Closeable
import java.io.FileInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

private const val MAX_ENTRIES = 20000
private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

private val IMAGES = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

/**
 * Reads page images from a CBZ in place through its file descriptor, so a large archive opens
 * without being copied. Pages are decoded downsampled to the size they will be shown at. Reads are
 * serialised because the archive shares one file channel. Duplicate file names and archives with
 * more than 20,000 entries are rejected; each page read is limited to 32 MiB before decoding.
 */
class CbzReader(resolver: ContentResolver, archive: Uri) : Closeable {
  private val descriptor =
    resolver.openFileDescriptor(archive, "r") ?: error("Cannot open $archive")
  private val channel =
    try {
      FileInputStream(descriptor.fileDescriptor).channel
    } catch (failure: Throwable) {
      descriptor.close()
      throw failure
    }
  private val zip =
    try {
      val opened = ZipFile.builder().setSeekableByteChannel(channel).get()
      try {
        val names = HashSet<String>()
        val entries = opened.entries
        var count = 0
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          require(++count <= MAX_ENTRIES) { "CBZ has too many entries" }
          require(entry.isDirectory || names.add(entry.name)) {
            "Duplicate CBZ entry: ${entry.name}"
          }
        }
        opened
      } catch (failure: Throwable) {
        opened.close()
        throw failure
      }
    } catch (failure: Throwable) {
      try {
        channel.close()
      } finally {
        descriptor.close()
      }
      throw failure
    }

  /** The page image [entry], downsampled by powers of two to roughly [maxWidth] × [maxHeight]. */
  fun page(entry: String, maxWidth: Int, maxHeight: Int): ImageBitmap {
    val bytes =
      synchronized(this) {
        val found = zip.getEntry(entry) ?: error("Page $entry is missing from the CBZ")
        require(!found.isDirectory) { "CBZ page is a directory: $entry" }
        require(found.size <= MAX_IMAGE_BYTES) { "CBZ page exceeds 32 MiB" }
        zip.getInputStream(found).use { input ->
          val data = input.readNBytes(MAX_IMAGE_BYTES + 1)
          require(data.size <= MAX_IMAGE_BYTES) { "CBZ page exceeds 32 MiB" }
          data
        }
      }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (
      bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight
    ) sample *= 2
    val bitmap =
      BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
      ) ?: error("Page $entry is not a readable image")
    return bitmap.asImageBitmap()
  }

  /**
   * The page images in natural filename order (page-2 before page-10): the reading order of a CBZ
   * no study document describes, as the server lists it.
   */
  fun pages(): List<String> =
    synchronized(this) {
      zip.entries
        .toList()
        .filter { !it.isDirectory }
        .map { it.name }
        .filter { name -> IMAGES.any { name.lowercase().endsWith(it) } }
        .sortedWith(NaturalOrder.COMPARATOR)
    }

  /** The first page image in [pages] order, or null if none. */
  fun firstPage(): String? = pages().firstOrNull()

  override fun close() {
    synchronized(this) {
      try {
        zip.close()
      } finally {
        try {
          channel.close()
        } finally {
          descriptor.close()
        }
      }
    }
  }
}
