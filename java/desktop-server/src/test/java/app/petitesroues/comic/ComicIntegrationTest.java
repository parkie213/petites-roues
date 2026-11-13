package app.petitesroues.comic;

import static app.petitesroues.support.HttpClients.command;
import static app.petitesroues.support.HttpClients.discover;
import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.model.document.ComicReference;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueItem;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.library.Library;
import app.petitesroues.support.DocumentFixtures;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Exercises actual repository registration, archive serving and SQLite resume persistence. */
class ComicIntegrationTest extends HttpIntegrationTest {
  @Test
  void browseReadResumeRefreshAndUnregisterComicWithoutVideoFields() throws Exception {
    // Manifest order deliberately differs from archive order and alphabetical order.
    var comic =
        DocumentFixtures.comic(
            "comic",
            "A comic",
            ReadingDirection.RTL,
            List.of(newPage("cover", "z.png", null), newPage("story", "a.png", "1")));
    var folder = root.resolve("comics/volume");
    Files.createDirectories(folder);
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(comic).getBytes(StandardCharsets.UTF_8));
    byte[] first = {1, 2, 3};
    byte[] second = {4, 5, 6};
    try (var zip = new ZipOutputStream(Files.newOutputStream(folder.resolve("book.cbz")))) {
      zip.putNextEntry(new ZipEntry("a.png"));
      zip.write(second);
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("z.png"));
      zip.write(first);
      zip.closeEntry();
    }
    // The CBZ links to the document by fingerprint, not by sitting beside it.
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec()
            .encode(
                comic.withBody(
                    comic
                        .comic()
                        .withReference(
                            new ComicReference(
                                MediaFingerprint.of(folder.resolve("book.cbz")), null))))
            .getBytes(StandardCharsets.UTF_8));
    var repository =
        discover(
            http,
            "add",
            null,
            Map.of("localPath", root.resolve("comics").toString()),
            JsonNode.class);
    var catalogue = http.get().uri("/api/library").retrieve().body(Library.class);
    var title = catalogue.repositories().getFirst().entries().getFirst();
    assertEquals("comic", title.type());
    assertNull(title.progress());
    assertTrue(title.mediaAvailable());
    assertFalse(title.videoAvailable());
    assertTrue(title.unavailable().isEmpty());
    String api = "/api/titles/" + title.id();
    var loaded = http.get().uri(api + "?view=study").retrieve().body(JsonNode.class);
    assertEquals(
        comic.comic().pages(),
        mapper.treeToValue(loaded.path("document"), StudyDocument.class).comic().pages());
    var exported = http.get().uri(api + "?download=true").retrieve().body(JsonNode.class);
    assertTrue(exported.has("comic"));
    assertTrue(exported.path("comic").path("pages").get(0).path("boxes").isEmpty());
    assertFalse(exported.has("durationSeconds"));
    assertInstanceOf(
        StudyDocument.class,
        new StudyDocumentReader(new StudyDocumentCodec())
            .decode(mapper.writeValueAsBytes(exported)));
    assertInstanceOf(StudyDocument.class, http.get().uri(api).retrieve().body(StudyDocument.class));
    var response = http.get().uri(api + "/comic/pages/cover").retrieve().toEntity(byte[].class);
    assertEquals("image/png", response.getHeaders().getContentType().toString());
    assertArrayEquals(first, response.getBody());
    assertArrayEquals(
        second, http.get().uri(api + "/comic/pages/story").retrieve().body(byte[].class));
    assertEquals(
        404,
        http.get()
            .uri(api + "/comic/pages/not-in-manifest")
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertTrue(
        http.get().uri(api + "/progress").retrieve().body(JsonNode.class).path("pageId").isNull());
    assertEquals(
        404,
        http.put()
            .uri(api + "/progress")
            .body(Map.of("pageId", "unknown"))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    http.put().uri(api + "/progress").body(Map.of("pageId", "story")).retrieve().toBodilessEntity();
    discover(http, "refresh", repository.path("id").asText(), null, ContentRepository.class);
    assertEquals(
        "story",
        http.get().uri(api + "/progress").retrieve().body(JsonNode.class).path("pageId").asText());
    // The library card shows how far through the comic the page is: page 2 of 2, then 1 of 2.
    assertEquals(1.0, libraryEntry().progress());
    http.put().uri(api + "/progress").body(Map.of("pageId", "cover")).retrieve().toBodilessEntity();
    assertEquals(0.5, libraryEntry().progress());
    // Clearing forgets the page: the card shows no progress and the comic opens at the start.
    http.delete().uri(api + "/progress").retrieve().toBodilessEntity();
    assertNull(libraryEntry().progress());
    assertTrue(
        http.get().uri(api + "/progress").retrieve().body(JsonNode.class).path("pageId").isNull());
    http.put().uri(api + "/progress").body(Map.of("pageId", "story")).retrieve().toBodilessEntity();
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM playback_progress", Integer.class));
    assertEquals(
        400,
        command(http, api, "timing", null, List.of())
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertEquals(
        400,
        http.put()
            .uri(api + "/progress")
            .body(Map.of("positionSeconds", 1))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    // Disappearing media is represented in the catalogue without losing the document or progress.
    Files.delete(folder.resolve("book.cbz"));
    title =
        http.get()
            .uri("/api/library")
            .retrieve()
            .body(Library.class)
            .repositories()
            .getFirst()
            .entries()
            .getFirst();
    assertFalse(title.mediaAvailable());
    assertTrue(title.documentAvailable());
    assertEquals(
        404,
        http.get()
            .uri(api + "?view=study")
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    http.delete()
        .uri("/api/repositories/" + repository.path("id").asText())
        .retrieve()
        .toBodilessEntity();
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reading_progress", Integer.class));
    assertTrue(Files.exists(folder.resolve("document.json")));
  }

  private CatalogueItem libraryEntry() {
    return http.get()
        .uri("/api/library")
        .retrieve()
        .body(Library.class)
        .repositories()
        .getFirst()
        .entries()
        .getFirst();
  }
}
