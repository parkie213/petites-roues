package app.petitesroues.android.comic

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.petitesroues.library.comic.CbzArchiveReader
import java.io.Closeable

/**
 * Android URI and image-decoding adapter for the shared CBZ reader. Keeps the URI's file descriptor
 * open without copying the archive, and downsamples decoded pages to their display size. The shared
 * reader owns archive validation, ordering, bounded decompression and serialized channel access.
 */
class CbzReader(resolver: ContentResolver, archive: Uri) : Closeable {
  private val input =
    AutoCloseInputStream(resolver.openFileDescriptor(archive, "r") ?: error("Cannot open $archive"))
  private val reader =
    try {
      CbzArchiveReader(input.channel)
    } catch (failure: Throwable) {
      try {
        input.close()
      } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
      }
      throw failure
    }

  /** The page image [entry], downsampled by powers of two to roughly [maxWidth] × [maxHeight]. */
  fun page(entry: String, maxWidth: Int, maxHeight: Int): ImageBitmap {
    val bytes = reader.read(entry)
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

  /** Image entries in natural filename order when no study document supplies a manifest. */
  fun pages(): List<String> = reader.pages()

  /** The first page image in [pages] order, or null if none. */
  fun firstPage(): String? = pages().firstOrNull()

  override fun close() {
    try {
      reader.close()
    } finally {
      input.close()
    }
  }
}
