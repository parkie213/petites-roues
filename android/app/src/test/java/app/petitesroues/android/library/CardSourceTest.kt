package app.petitesroues.android.library

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.library.shelves.Shelves
import app.petitesroues.library.shelves.Shelves.CardArt
import app.petitesroues.library.shelves.Shelves.Shape
import org.junit.Assert.assertEquals
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
  fun aPortraitCardWithoutArtOrFrameHasNoImage() {
    assertNull(CardSource.of(null))
    assertNull(CardSource.of(CardArt(title(), false)))
  }

  @Test
  fun aLandscapeCardShowsAFrameOfTheVideoTheSharedRulesChose() {
    val clip = title()
    assertEquals(CardSource.Frame(clip.mediaUri!!), CardSource.of(null, clip))
  }

  @Test
  fun aPosterWinsOverAFrame() {
    val poster = Uri.parse("file:///media/poster.jpg")
    val clip = title(poster = poster)
    assertEquals(CardSource.Poster(poster), CardSource.of(CardArt(clip, false), clip))
  }

  @Test
  fun aFrameOfAVideoWithoutMediaIsNoImage() {
    assertNull(CardSource.of(null, title(media = null)))
  }

  @Test
  fun framesAreCachedUnderTheirOwnKey() {
    val poster = Uri.parse("file:///media/poster.jpg")
    assertEquals("file:///media/poster.jpg", CardSource.Poster(poster).key)
    assertEquals("file:///media/clip.mp4#frame", CardSource.Frame(title().mediaUri!!).key)
  }

  @Test
  fun eachCardOnAMixedShelfTakesItsShapeAndImageFromTheSharedRules() {
    val poster = Uri.parse("file:///media/poster.jpg")
    val comic =
      title(kind = TitleKind.COMIC)
        .copy(documentId = "comic", name = "Comic", shelf = "Mixed", firstPage = "p1")
    val wide =
      title()
        .copy(
          documentId = "clip-1",
          name = "Clip",
          shelf = "Mixed",
          documentUri = Uri.parse("file:///d"),
        )
    val tall =
      title(poster = poster)
        .copy(
          documentId = "trailer",
          name = "Trailer",
          shelf = "Mixed",
          documentUri = Uri.parse("file:///e"),
        )
    val cards = Shelves.shelves(listOf(comic, wide, tall)).single().groups.associateBy { it.name }
    assertEquals(Shape.PORTRAIT, cards.getValue("Comic").shape())
    assertEquals(Shape.LANDSCAPE, cards.getValue("Clip").shape())
    assertEquals(Shape.PORTRAIT, cards.getValue("Trailer").shape())
    val clip = cards.getValue("Clip")
    assertEquals(CardSource.Frame(wide.mediaUri!!), CardSource.of(clip.art, clip.frame))
    val trailer = cards.getValue("Trailer")
    assertEquals(CardSource.Poster(poster), CardSource.of(trailer.art, trailer.frame))
  }
}
