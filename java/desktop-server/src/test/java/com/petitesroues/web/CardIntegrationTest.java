package com.petitesroues.web;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.ComicReference;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.library.CatalogueItem;
import com.petitesroues.model.library.Library;
import com.petitesroues.support.DocumentFixtures;
import com.petitesroues.support.HttpIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Card images through the real catalogue, CBZ reader, ffmpeg and cache directory. */
class CardIntegrationTest extends HttpIntegrationTest {
  @TempDir static Path cache;

  @DynamicPropertySource
  static void cacheDirectory(DynamicPropertyRegistry registry) {
    registry.add("petitesroues.cards.cache", () -> cache.toString());
  }

  @Test
  void comicCoverPosterOverrideSeriesPosterMediaCardsAndPlaceholder() throws Exception {
    Path series = root.resolve("manga/demon-slayer");
    Path tome = Files.createDirectories(series.resolve("t01"));
    var comic =
        DocumentFixtures.comic(
            "tome-1",
            "Demon Slayer — Tome 1",
            ReadingDirection.RTL,
            List.of(newPage("cover", "z.png", null), newPage("story", "a.png", "1")));
    comic =
        comic.withBody(
            new ComicDocument(
                1, comic.comic().readingDirection(), null, comic.comic().pages(), List.of()));
    Files.writeString(
        tome.resolve("document.json"),
        new StudyDocumentCodec().encode(comic),
        StandardCharsets.UTF_8);
    try (var zip = new ZipOutputStream(Files.newOutputStream(tome.resolve("book.cbz")))) {
      for (var entry : Map.of("a.png", image(40, 60), "z.png", image(1200, 1800)).entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    // The CBZ links to the document by fingerprint, not by sitting beside it.
    Files.writeString(
        tome.resolve("document.json"),
        new StudyDocumentCodec()
            .encode(
                comic.withBody(
                    comic
                        .comic()
                        .withReference(
                            new ComicReference(
                                MediaFingerprint.of(tome.resolve("book.cbz")), null)))),
        StandardCharsets.UTF_8);
    http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", root.resolve("manga").toString()))
        .retrieve()
        .toBodilessEntity();

    var title = onlyTitle();
    assertEquals(1, title.volume());
    assertFalse(title.posterAvailable());
    assertFalse(title.seriesPosterAvailable());
    String api = "/api/titles/" + title.id();

    // Without a poster, a comic's card is its first manifest page, downscaled to card width.
    String coverUrl = location(api + "/card");
    var cover = image(api + "/card");
    assertEquals(480, cover.getWidth());
    assertEquals(720, cover.getHeight());
    assertEquals(404, status(api + "/series-card"));

    // A poster in the CBZ's own folder, found by Refresh, replaces the cover under a new URL; small
    // images stay small.
    Files.write(tome.resolve("poster.png"), image(100, 150));
    http.post().uri("/api/repositories/refresh").retrieve().toBodilessEntity();
    assertTrue(onlyTitle().posterAvailable());
    assertEquals(100, image(api + "/card").getWidth());
    assertNotEquals(coverUrl, location(api + "/card"), "replaced source must change the URL");

    // Replacing the poster's content changes the version; an old version redirects to the new one.
    String posterUrl = location(api + "/card");
    Files.write(tome.resolve("poster.png"), image(90, 150));
    Files.setLastModifiedTime(tome.resolve("poster.png"), FileTime.fromMillis(1));
    String newPosterUrl = location(api + "/card");
    assertNotEquals(posterUrl, newPosterUrl);
    assertEquals(newPosterUrl, "card/" + location(api + "/" + posterUrl));
    assertEquals(90, image(api + "/card").getWidth());

    // The nearest poster above the CBZ's own folder is the series card, the registered folder
    // included (it may be the series folder itself).
    Files.write(root.resolve("manga/poster.jpg"), image(10, 10));
    http.post().uri("/api/repositories/refresh").retrieve().toBodilessEntity();
    assertTrue(onlyTitle().seriesPosterAvailable());
    assertEquals(10, image(api + "/series-card").getWidth());
    Files.write(series.resolve("poster.png"), image(300, 450));
    http.post().uri("/api/repositories/refresh").retrieve().toBodilessEntity();
    assertEquals(300, image(api + "/series-card").getWidth());

    // A CBZ no document references has the same cards by fingerprint: its first page by name.
    Path loose = Files.createDirectories(root.resolve("manga/loose"));
    try (var zip = new ZipOutputStream(Files.newOutputStream(loose.resolve("extra.cbz")))) {
      for (var entry : Map.of("b.jpg", image(60, 90), "c.jpg", image(30, 90)).entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    http.post().uri("/api/repositories/refresh").retrieve().toBodilessEntity();
    var media =
        http
            .get()
            .uri("/api/library")
            .retrieve()
            .body(Library.class)
            .repositories()
            .getFirst()
            .media()
            .stream()
            .filter(item -> item.titleId() == null)
            .findFirst()
            .orElseThrow();
    assertFalse(media.posterAvailable());
    assertTrue(media.seriesPosterAvailable());
    String mediaApi = "/api/media/" + media.fingerprint();
    assertEquals(60, image(mediaApi + "/card").getWidth());
    assertEquals(10, image(mediaApi + "/series-card").getWidth());
    Files.write(loose.resolve("extra.webp"), image(50, 75));
    http.post().uri("/api/repositories/refresh").retrieve().toBodilessEntity();
    assertEquals(50, image(mediaApi + "/card").getWidth());

    // Without a poster or CBZ there is no card image: the library draws a placeholder.
    Files.delete(tome.resolve("poster.png"));
    Files.delete(tome.resolve("book.cbz"));
    assertEquals(404, status(api + "/card"));
  }

  private CatalogueItem onlyTitle() {
    return http.get()
        .uri("/api/library")
        .retrieve()
        .body(Library.class)
        .repositories()
        .getFirst()
        .entries()
        .getFirst();
  }

  /** Follows the version redirect, as a browser does, and decodes the JPEG. */
  private BufferedImage image(String url) throws Exception {
    String versioned = url.substring(0, url.lastIndexOf('/') + 1) + location(url);
    ResponseEntity<byte[]> response = http.get().uri(versioned).retrieve().toEntity(byte[].class);
    assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
    assertTrue(response.getHeaders().getCacheControl().contains("immutable"));
    return ImageIO.read(new ByteArrayInputStream(response.getBody()));
  }

  /** The relative redirect target of an unversioned card URL. */
  private String location(String url) {
    return http.get()
        .uri(url)
        .exchange(
            (request, response) -> {
              assertEquals(302, response.getStatusCode().value());
              return response.getHeaders().getLocation().toString();
            });
  }

  private int status(String url) {
    return http.get()
        .uri(url)
        .<Integer>exchange((request, response) -> response.getStatusCode().value());
  }

  private static byte[] image(int width, int height) throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
    return output.toByteArray();
  }
}
