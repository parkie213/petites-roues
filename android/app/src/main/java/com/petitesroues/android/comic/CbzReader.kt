package com.petitesroues.android.comic

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.Closeable
import java.io.FileInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Reads page images from a CBZ in place through its file descriptor, so a large archive opens
 * without being copied. Pages are decoded downsampled to the size they will be shown at. Reads are
 * serialised because the archive shares one file channel.
 */
class CbzReader(resolver: ContentResolver, archive: Uri) : Closeable {
  private val descriptor =
    resolver.openFileDescriptor(archive, "r") ?: error("Cannot open $archive")
  private val channel = FileInputStream(descriptor.fileDescriptor).channel
  private val zip = ZipFile.builder().setSeekableByteChannel(channel).get()

  /** The page image [entry], downsampled by powers of two to roughly [maxWidth] × [maxHeight]. */
  fun page(entry: String, maxWidth: Int, maxHeight: Int): ImageBitmap {
    val bytes =
      synchronized(this) {
        val found = zip.getEntry(entry) ?: error("Page $entry is missing from the CBZ")
        zip.getInputStream(found).use { it.readBytes() }
      }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight)
      sample *= 2
    val bitmap =
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Page $entry is not a readable image")
    return bitmap.asImageBitmap()
  }

  override fun close() {
    zip.close()
    channel.close()
    descriptor.close()
  }
}
