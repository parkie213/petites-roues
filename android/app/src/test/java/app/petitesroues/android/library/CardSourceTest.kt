package app.petitesroues.android.library

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.library.shelves.Shelves.CardArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class CardSourceTest {
  private fun title(
    kind: TitleKind = TitleKind.VIDEO,
    poster: Uri? = null,
    media: Uri? = Uri.parse("file:///media/clip.mp4"),
  ) =
    Title(
      path = "f/clip",
      name = "Clip",
      series = null,
      kind = kind,
      documentUri = null,
      mediaUri = media,
      documentId = "clip",
      durationSeconds = null,
      season = null,
      episode = null,
      volume = null,
      poster = poster,
      seriesPoster = null,
      firstPage = null,
    )

  @Test
  fun aPortraitCardWithoutArtHasNoImage() {
    assertNull(CardSource.of(null, false, listOf(title())))
    assertNull(CardSource.of(CardArt(title(), false), false, listOf(title())))
  }

  @Test
  fun aLandscapeCardWithoutAPosterShowsAFrameOfItsVideo() {
    val clip = title()
    val frame = CardSource.Frame(clip.mediaUri!!)
    assertEquals(frame, CardSource.of(null, true, listOf(clip)))
    assertEquals(
      frame,
      CardSource.of(CardArt(clip, false), true, listOf(clip)),
    )
  }

  @Test
  fun aLandscapeCardPrefersItsPosterAndKeepsItWide() {
    val poster = Uri.parse("file:///media/poster.jpg")
    val clip = title(poster = poster)
    assertEquals(
      CardSource.Poster(poster, wide = true),
      CardSource.of(CardArt(clip, false), true, listOf(clip)),
    )
    assertEquals(
      CardSource.Poster(poster),
      CardSource.of(CardArt(clip, false), false, listOf(clip)),
    )
  }

  @Test
  fun aLandscapeSeriesFramesItsFirstVideoThatIsPresent() {
    val missing = title(media = null)
    val present = title()
    assertEquals(
      CardSource.Frame(present.mediaUri!!),
      CardSource.of(null, true, listOf(missing, present)),
    )
    assertNull(CardSource.of(null, true, listOf(missing)))
  }

  @Test
  fun aComicIsNeverFramed() {
    assertNull(CardSource.of(null, true, listOf(title(kind = TitleKind.COMIC))))
  }

  @Test
  fun wideAndPortraitCopiesOfOnePosterAreCachedSeparately() {
    val poster = Uri.parse("file:///media/poster.jpg")
    assertNotEquals(CardSource.Poster(poster).key, CardSource.Poster(poster, true).key)
    assertEquals("file:///media/clip.mp4#frame", CardSource.Frame(title().mediaUri!!).key)
  }
}
