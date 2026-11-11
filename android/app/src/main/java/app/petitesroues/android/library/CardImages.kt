package app.petitesroues.android.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import app.petitesroues.android.comic.CbzReader
import app.petitesroues.library.shelves.Shelves.CardArt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a card image comes from: a poster file, or a page inside a CBZ. */
sealed interface CardSource {
  val key: String

  data class Poster(val uri: Uri) : CardSource {
    override val key = uri.toString()
  }

  /** A comic page: [entry], or with none the CBZ's first image (media without a document). */
  data class Page(val archive: Uri, val entry: String?) : CardSource {
    override val key = "$archive#$entry"
  }

  companion object {
    /** The image for [art]: a series poster, else the title's poster or a comic's first page. */
    fun of(art: CardArt<Title>?): CardSource? {
      val title = art?.copy ?: return null
      if (art.series) return title.seriesPoster?.let(::Poster)
      title.poster?.let {
        return Poster(it)
      }
      val archive = title.mediaUri ?: return null
      return if (title.kind == TitleKind.COMIC) Page(archive, title.firstPage) else null
    }
  }
}

/**
 * Card-sized library images, decoded downsampled and kept in memory and in the app's cache folder,
 * so comics do not reopen their CBZ on every visit. The disk copies are cleared on refresh, when
 * posters may have changed.
 */
@Singleton
class CardImages @Inject constructor(@ApplicationContext private val context: Context) {
  private val memory = LruCache<String, ImageBitmap>(64)
  private val folder = File(context.cacheDir, "cards")

  /** The card image for [source], or null when it cannot be read. */
  suspend fun load(source: CardSource): ImageBitmap? {
    memory.get(source.key)?.let {
      return it
    }
    return withContext(Dispatchers.IO) {
      runCatching {
        val file = File(folder, sha1(source.key) + ".jpg")
        val image =
          BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            ?: fit(decode(source)).also { image ->
              folder.mkdirs()
              file.outputStream().use {
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 85, it)
              }
            }
        memory.put(source.key, image)
        image
      }
        .getOrNull()
    }
  }

  /** Forgets every cached card, after a refresh. */
  fun clear() {
    memory.evictAll()
    folder.deleteRecursively()
  }

  private fun decode(source: CardSource): ImageBitmap =
    when (source) {
      is CardSource.Page ->
        CbzReader(context.contentResolver, source.archive).use { reader ->
          val entry =
            source.entry ?: reader.firstPage() ?: error("${source.archive} has no page images")
          reader.page(entry, WIDTH, HEIGHT)
        }
      is CardSource.Poster -> {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (
          bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT
        ) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap =
          resolver.openInputStream(source.uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
          } ?: error("Cannot read ${source.uri}")
        bitmap.asImageBitmap()
      }
    }

  /** Scales [image] down to the card width, so cached cards stay small. */
  private fun fit(image: ImageBitmap): ImageBitmap {
    if (image.width <= WIDTH) return image
    val bitmap = image.asAndroidBitmap()
    return bitmap.scale(WIDTH, bitmap.height * WIDTH / bitmap.width).asImageBitmap()
  }

  private fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") {
      "%02x".format(it)
    }

  private companion object {
    // Twice a phone card's size, so cards stay sharp on a dense screen.
    const val WIDTH = 360
    const val HEIGHT = 540
  }
}
