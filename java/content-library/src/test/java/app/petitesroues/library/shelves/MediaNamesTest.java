package app.petitesroues.library.shelves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.petitesroues.library.shelves.MediaNames.Named;
import org.junit.jupiter.api.Test;

class MediaNamesTest {
  private static Named named(String path) {
    return MediaNames.fromPath(path);
  }

  @Test
  void mediaIsNamedFromItsPathTheWayMediaServersReadIt() {
    assertEquals(
        new Named("Peppa pig", 1, 1, "Les flaques de boue"),
        named("peppa-pig/s01e01-les-flaques-de-boue.mp4"));
    assertEquals(
        new Named("Masha and the bear", null, 83, "Bienenalarm"),
        named("masha-and-the-bear/083-bienenalarm.de.mp4"));
    assertEquals(new Named("Show", 2, null, "Show E05"), named("Show/Season 02/Show - E05.mkv"));
    assertEquals(
        new Named("Peppa pig", 1, 3, "Ma meilleure amie"),
        named("peppa-pig/s01e03-ma-meilleure-amie/video.mp4"));
    assertEquals(
        new Named(null, null, null, "Le Chateau Ambulant"), named("Le Chateau Ambulant.mkv"));
    assertEquals(new Named("Films", null, null, "2046"), named("films/2046.mkv"));
  }

  @Test
  void aComicTomeIsNamedByItsFileInItsSeriesFolder() {
    assertEquals(
        new Named("Naruto", null, null, "Naruto T02"), named("comics/naruto/Naruto-T02.cbz"));
  }
}
