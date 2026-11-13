package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.support.DocumentFixtures;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/**
 * The skeleton endpoint over a registered, refreshed media folder: its output equals the dictionary
 * {@code prepare/new_title.py} built before the app took over, and every refusal is a 4xx.
 */
class TitleSkeletonIntegrationTest extends HttpIntegrationTest {
  private Path media;

  private void register() throws Exception {
    media = root.resolve("media");
    Files.createDirectories(media);
    Files.copy(root.resolve("video.mp4"), media.resolve("clip.mp4"));
    try (var zip = new ZipOutputStream(Files.newOutputStream(media.resolve("Tome 02.cbz")))) {
      for (String name : List.of("page-10.png", "Page-2.png", "page-1.png", "notes.txt")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(name.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    try (var zip = new ZipOutputStream(Files.newOutputStream(media.resolve("empty.cbz")))) {
      zip.putNextEntry(new ZipEntry("readme.txt"));
      zip.write(1);
      zip.closeEntry();
    }
    http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", media.toString()))
        .retrieve()
        .toBodilessEntity();
  }

  private JsonNode skeleton(Map<String, Object> request) {
    return mapper.readTree(
        http.post().uri("/api/titles/skeleton").body(request).retrieve().body(String.class));
  }

  private String refusal(Map<String, Object> request, int status) {
    var failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            HttpClientErrorException.class,
            () ->
                http.post()
                    .uri("/api/titles/skeleton")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(status, failure.getStatusCode().value());
    return mapper.readTree(failure.getResponseBodyAsString()).path("error").asString();
  }

  @Test
  void aVideoSkeletonEqualsWhatTheScriptBuilt() throws Exception {
    register();
    Path video = media.resolve("clip.mp4");
    var indexed = mapper.readTree(http.get().uri("/api/library").retrieve().body(String.class));
    double duration =
        indexed
            .path("repositories")
            .valueStream()
            .flatMap(folder -> folder.path("media").valueStream())
            .filter(item -> "clip.mp4".equals(item.path("relativePath").asString()))
            .findFirst()
            .orElseThrow()
            .path("durationSeconds")
            .asDouble();
    var reference = new LinkedHashMap<String, Object>();
    reference.put("fingerprint", MediaFingerprint.of(video));
    reference.put("sizeBytes", Files.size(video));
    reference.put("durationSeconds", Math.round(duration * 1000) / 1000.0);
    var expected = new LinkedHashMap<String, Object>();
    expected.put("id", "peppa-s01e01");
    expected.put("title", "Peppa S01E01");
    expected.put("sourceLanguage", "en");
    expected.put("translationLanguage", "fr");
    expected.put("notesLanguage", "en");
    expected.put("series", "Peppa");
    expected.put("video", Map.of("reference", reference, "cues", List.of()));

    var result =
        skeleton(
            Map.of(
                "media",
                video.toString(),
                "title",
                " Peppa S01E01 ",
                "series",
                "Peppa",
                "sourceLanguage",
                "en",
                "translationLanguage",
                "fr"));
    assertEquals(
        mapper.readValue(mapper.writeValueAsString(expected), Map.class),
        mapper.convertValue(result, Map.class));
    assertEquals(
        List.of(
            "id",
            "title",
            "sourceLanguage",
            "translationLanguage",
            "notesLanguage",
            "series",
            "video"),
        result.propertyNames().stream().toList());
    assertEquals(
        List.of("reference", "cues"), result.path("video").propertyNames().stream().toList());
  }

  @Test
  void aComicSkeletonListsPagesInNaturalOrderAndReadsRightToLeftByDefault() throws Exception {
    register();
    Path cbz = media.resolve("Tome 02.cbz");
    var expected = new LinkedHashMap<String, Object>();
    expected.put("id", "tome-02");
    expected.put("title", "Tome 02");
    expected.put("sourceLanguage", "fr");
    expected.put("translationLanguage", "en");
    expected.put("notesLanguage", "en");
    expected.put(
        "comic",
        Map.of(
            "volume",
            2,
            "readingDirection",
            "rtl",
            "reference",
            Map.of("fingerprint", MediaFingerprint.of(cbz), "sizeBytes", Files.size(cbz)),
            "pages",
            List.of(
                Map.of("id", "page-001", "entry", "page-1.png", "label", "1", "boxes", List.of()),
                Map.of("id", "page-002", "entry", "Page-2.png", "label", "2", "boxes", List.of()),
                Map.of(
                    "id", "page-003", "entry", "page-10.png", "label", "3", "boxes", List.of()))));

    var result = skeleton(Map.of("media", cbz.toString(), "title", "Tome 02", "volume", 2));
    // The app also writes the empty chapter list; the script omitted it.
    var comic = (tools.jackson.databind.node.ObjectNode) result.path("comic");
    assertTrue(comic.path("chapters").isEmpty());
    comic.remove("chapters");
    assertEquals(
        mapper.readValue(mapper.writeValueAsString(expected), Map.class),
        mapper.convertValue(result, Map.class));
    assertEquals(
        List.of("volume", "readingDirection", "reference", "pages"),
        result.path("comic").propertyNames().stream().toList());
    assertEquals(
        "ltr",
        skeleton(Map.of("media", cbz.toString(), "title", "Tome 02", "leftToRight", true))
            .path("comic")
            .path("readingDirection")
            .asString());
  }

  @Test
  void refusesWhatCannotMakeAValidDocument() throws Exception {
    register();
    String video = media.resolve("clip.mp4").toString();
    String outside = root.resolve("video.mp4").toString();
    assertTrue(refusal(Map.of("media", outside, "title", "X"), 400).contains("Add or Refresh"));
    assertTrue(
        refusal(Map.of("media", root.resolve("nothing.mp4").toString(), "title", "X"), 400)
            .contains("Add or Refresh"));
    assertTrue(refusal(Map.of("media", "clip.mp4", "title", "X"), 400).contains("absolute"));
    assertTrue(refusal(Map.of("media", video, "title", " "), 400).contains("title"));
    assertTrue(refusal(Map.of("media", video, "title", "!!!"), 400).contains("ID"));
    assertTrue(refusal(Map.of("media", video, "title", "X", "volume", 1), 400).contains("comics"));
    assertTrue(
        refusal(Map.of("media", media.resolve("empty.cbz").toString(), "title", "X"), 400)
            .contains("no page images"));

    jdbc.update("UPDATE media SET audio_languages='en,fr' WHERE relative_path='clip.mp4'");
    String language = refusal(Map.of("media", video, "title", "X"), 400);
    assertTrue(language.contains("plays en audio first, not fr (0:a:0 en, 0:a:1 fr)"), language);
    assertTrue(language.contains("Put 0:a:1 first"), language);
    skeleton(Map.of("media", video, "title", "X", "ignoreTrackLanguage", true));
    skeleton(Map.of("media", video, "title", "X", "sourceLanguage", "en"));

    // Move the file's modification time: the index no longer describes it.
    Files.setLastModifiedTime(
        media.resolve("clip.mp4"), java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
    assertTrue(refusal(Map.of("media", video, "title", "X"), 400).contains("changed since"));
  }

  @Test
  void refusesATitleIdThatIsAlreadyKnown() throws Exception {
    register();
    var folder = root.resolve("docs/taken");
    Files.createDirectories(folder);
    Files.writeString(
        folder.resolve("document.json"),
        new app.petitesroues.documents.StudyDocumentCodec()
            .encode(DocumentFixtures.document("taken")),
        StandardCharsets.UTF_8);
    http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", root.resolve("docs").toString()))
        .retrieve()
        .toBodilessEntity();
    String message =
        refusal(Map.of("media", media.resolve("clip.mp4").toString(), "title", "Taken"), 409);
    assertTrue(message.contains("taken") && message.contains("already exists"), message);
  }
}
