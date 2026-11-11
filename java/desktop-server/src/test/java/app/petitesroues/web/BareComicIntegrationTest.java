package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

/** Reading a CBZ that no study document references, page by page, by its fingerprint. */
class BareComicIntegrationTest extends HttpIntegrationTest {
  @Test
  void aCbzWithoutADocumentIsReadInNaturalPageOrder() throws Exception {
    Path cbz = root.resolve("media/comics/asterix/01-Asterix-le-Gaulois.cbz");
    Files.createDirectories(cbz.getParent());
    try (var zip = new ZipOutputStream(Files.newOutputStream(cbz))) {
      for (String name : List.of("page-10.jpg", "notes.txt", "page-2.png", "page-1.jpg")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(name.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", root.resolve("media").toString()))
        .retrieve()
        .toBodilessEntity();
    String base = "/api/media/" + MediaFingerprint.of(cbz) + "/pages";

    List<String> pages =
        mapper.readValue(
            http.get().uri(base).retrieve().body(String.class), new TypeReference<>() {});
    assertEquals(List.of("page-1.jpg", "page-2.png", "page-10.jpg"), pages, "only images, 2 < 10");

    var second = http.get().uri(base + "/1").retrieve().toEntity(byte[].class);
    assertEquals("page-2.png", new String(second.getBody(), StandardCharsets.UTF_8));
    assertEquals("image/png", second.getHeaders().getContentType().toString());

    assertEquals(404, status(base + "/3"), "past the last page");
    assertEquals(404, status("/api/media/v1:unknown/pages"));
  }

  private int status(String uri) {
    return http.get().uri(uri).exchange((request, response) -> response.getStatusCode()).value();
  }
}
