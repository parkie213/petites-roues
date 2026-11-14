package app.petitesroues.android.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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

/**
 * Where a card image comes from: a poster file, a page inside a CBZ, or a frame of a video. A
 * [wide] poster is for a landscape card, so it is kept at a larger size.
 */
sealed interface CardSource {
  val key: String

  data class Poster(val uri: Uri, val wide: Boolean = false) : CardSource {
    override val key = if (wide) "$uri#wide" else uri.toString()
  }

  /**
   * A frame grabbed from [video] about a quarter of the way in ([framePositionMicros]) and
   * center-cropped to 16:9 ([landscapeCrop]), for a landscape card whose video has no poster.
   */
  data class Frame(val video: Uri) : CardSource {
    override val key = "$video#frame"
  }

  /** A comic page: [entry], or with none the CBZ's first image (media without a document). */
  data class Page(val archive: Uri, val entry: String?) : CardSource {
    override val key = "$archive#$entry"
  }

  companion object {
    /**
     * The image for [art]: a series poster, else the title's poster or a comic's first page. On a
     * [landscape] shelf a card without art shows a frame of the first of [videos] that is present;
     * [videos] are the card's own titles, which [art] does not name when it has no art at all.
     */
    fun of(
      art: CardArt<Title>?,
      landscape: Boolean = false,
      videos: List<Title> = emptyList(),
    ): CardSource? =
      posterOrPage(art, landscape)
        ?: if (landscape)
          videos
            .firstOrNull { it.kind == TitleKind.VIDEO && it.mediaUri != null }
            ?.mediaUri
            ?.let(::Frame)
        else null

    private fun posterOrPage(art: CardArt<Title>?, wide: Boolean): CardSource? {
      val title = art?.copy ?: return null
      if (art.series) return title.seriesPoster?.let { Poster(it, wide) }
      title.poster?.let {
        return Poster(it, wide)
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
            ?: fit(decode(source), source.width).also { image ->
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
      is CardSource.Frame -> grabFrame(source.video)
      is CardSource.Poster -> {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val width = source.width
        val height = if (source.wide) WIDE_HEIGHT else HEIGHT
        while (
          bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height
        ) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap =
          resolver.openInputStream(source.uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
          } ?: error("Cannot read ${source.uri}")
        bitmap.asImageBitmap()
      }
    }

  /**
   * A 16:9 frame of [video], a quarter of the way through it, center-cropped and scaled to the wide
   * card width. Fails when the video cannot be read or has no decodable frame.
   */
  private fun grabFrame(video: Uri): ImageBitmap {
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, video)
      val duration =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
      val frame =
        retriever.getFrameAtTime(
          framePositionMicros(duration),
          MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        ) ?: error("No frame in $video")
      val crop = landscapeCrop(frame.width, frame.height)
      val cropped = Bitmap.createBitmap(frame, crop.x, crop.y, crop.width, crop.height)
      return cropped.asImageBitmap()
    } finally {
      retriever.release()
    }
  }

  /** Scales [image] down to [width], so cached cards stay small. */
  private fun fit(image: ImageBitmap, width: Int): ImageBitmap {
    if (image.width <= width) return image
    val bitmap = image.asAndroidBitmap()
    return bitmap.scale(width, bitmap.height * width / bitmap.width).asImageBitmap()
  }

  /** The width a card from [this] source is kept at. */
  private val CardSource.width: Int
    get() =
      if (this is CardSource.Frame || (this is CardSource.Poster && wide)) WIDE_WIDTH else WIDTH

  private fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") {
      "%02x".format(it)
    }

  private companion object {
    // Twice a phone card's size, so cards stay sharp on a dense screen.
    const val WIDTH = 360
    const val HEIGHT = 540

    // A landscape card is about twice as wide, 16:9.
    const val WIDE_WIDTH = 480
    const val WIDE_HEIGHT = 270
  }
}
