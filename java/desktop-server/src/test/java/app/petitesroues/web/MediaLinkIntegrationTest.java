package app.petitesroues.web;

import static app.petitesroues.support.HttpClients.discover;
import static app.petitesroues.support.SpecFixtures.newPage;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.model.document.ComicReference;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.support.DocumentFixtures;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/** Linking media to documents by hand, through the API, over real folders and Refresh. */
class MediaLinkIntegrationTest extends HttpIntegrationTest {
  private static final String NOWHERE = "v1:" + "0".repeat(64);

  private static Path cbz(Path file, String marker) throws Exception {
    Files.createDirectories(file.getParent());
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("page-001.png"));
      zip.write(marker.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return file;
  }

  private static void comic(Path file, String id, String fingerprint) throws Exception {
    StudyDocument comic =
        DocumentFixtures.comic(
            id, id, ReadingDirection.LTR, List.of(newPage("p1", "page-001.png", "1")));
    write(file, comic.withComic(body -> body.withReference(new ComicReference(fingerprint, null))));
  }

  private static void write(Path file, StudyDocument document) throws Exception {
    Files.createDirectories(file.getParent());
    Files.writeString(file, new StudyDocumentCodec().encode(document), StandardCharsets.UTF_8);
  }

  private String add(Path folder) {
    return discover(
            http, "add", null, Map.of("localPath", folder.toString()), ContentRepository.class)
        .id();
  }

  private JsonNode library() {
    return mapper.readTree(http.get().uri("/api/library").retrieve().body(String.class));
  }

  private JsonNode title(String documentId) {
    return library()
        .path("repositories")
        .valueStream()
        .flatMap(folder -> folder.path("entries").valueStream())
        .filter(entry -> documentId.equals(entry.path("documentId").asString()))
        .findFirst()
        .orElseThrow();
  }

  private JsonNode media(String relativePath) {
    return library()
        .path("repositories")
        .valueStream()
        .flatMap(folder -> folder.path("media").valueStream())
        .filter(item -> relativePath.equals(item.path("relativePath").asString()))
        .findFirst()
        .orElseThrow();
  }

  private void link(String folder, String path, String titleId) {
    http.put()
        .uri("/api/library/media-links")
        .body(Map.of("repositoryId", folder, "relativePath", path, "titleId", titleId))
        .retrieve()
        .toBodilessEntity();
  }

  private void unlink(String folder, String path) {
    http.delete()
        .uri("/api/library/media-links?repository={r}&path={p}", folder, path)
        .retrieve()
        .toBodilessEntity();
  }

  private void refresh() {
    discover(http, "refresh", null, null, app.petitesroues.model.library.ContentRepository.class);
  }

  @Test
  void aHandLinkWinsOnBothSidesSurvivesRefreshAndUnlinks() throws Exception {
    Path media = root.resolve("media"), docs = root.resolve("docs");
    cbz(media.resolve("lost.cbz"), "lost");
    Path shared = cbz(media.resolve("shared.cbz"), "shared");
    comic(docs.resolve("stray/document.json"), "stray", NOWHERE);
    comic(docs.resolve("owner/document.json"), "owner", MediaFingerprint.of(shared));
    write(docs.resolve("film/document.json"), DocumentFixtures.document("film"));
    String mediaId = add(media);
    add(docs);

    var stray = title("stray");
    assertFalse(stray.path("mediaAvailable").asBoolean());
    assertEquals(
        "No CBZ matches its reference", stray.path("unavailable").path("title").asString());
    assertEquals(List.of("No study document"), warnings(media("lost.cbz")));
    String strayId = stray.path("id").asString();

    link(mediaId, "lost.cbz", strayId);
    stray = title("stray");
    assertTrue(stray.path("mediaAvailable").asBoolean());
    assertTrue(stray.path("mediaLinkedByHand").asBoolean());
    assertEquals("lost.cbz", stray.path("mediaPath").asString());
    assertEquals(strayId, media("lost.cbz").path("titleId").asString());
    assertTrue(media("lost.cbz").path("linkedByHand").asBoolean());
    assertEquals(List.of(), warnings(media("lost.cbz")));

    link(mediaId, "shared.cbz", strayId);
    assertFalse(
        title("owner").path("mediaAvailable").asBoolean(),
        "a hand-linked file no longer links to the document its fingerprint names");
    assertEquals(strayId, media("shared.cbz").path("titleId").asString());

    refresh();
    assertTrue(title("stray").path("mediaLinkedByHand").asBoolean(), "links survive Refresh");
    assertEquals(strayId, media("shared.cbz").path("titleId").asString());

    unlink(mediaId, "shared.cbz");
    assertTrue(title("owner").path("mediaAvailable").asBoolean(), "back to the fingerprint");
    assertFalse(media("shared.cbz").path("linkedByHand").asBoolean());

    var refused =
        assertThrows(
            HttpClientErrorException.class,
            () -> link(mediaId, "lost.cbz", title("film").path("id").asString()));
    assertEquals(400, refused.getStatusCode().value());
    assertThrows(HttpClientErrorException.class, () -> unlink(mediaId, "shared.cbz"));

    Files.move(media.resolve("lost.cbz"), media.resolve("renamed.cbz"));
    refresh();
    assertFalse(title("stray").path("mediaAvailable").asBoolean(), "a moved file loses its link");

    http.delete().uri("/api/repositories/{id}", mediaId).retrieve().toBodilessEntity();
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM media_links", Integer.class),
        "links go with their folder");
  }

  private static List<String> warnings(JsonNode item) {
    return item.path("warnings").valueStream().map(JsonNode::asString).toList();
  }
}
