package com.petitesroues.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.petitesroues.media.Ffmpeg;
import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.model.document.ComicReference;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.support.HttpIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/** Creating titles from a file on this computer: media copy, empty document and indexing. */
class TitleImportIntegrationTest extends HttpIntegrationTest {
  String repositoryId;
  Path repository;

  @BeforeEach
  void repository() throws Exception {
    repository = Files.createDirectories(root.resolve("repo"));
    repositoryId =
        http.post()
            .uri("/api/repositories")
            .body(Map.of("localPath", repository.toString()))
            .retrieve()
            .body(JsonNode.class)
            .get("id")
            .asString();
  }

  /** Starts an import and waits for it, as the Add title dialog does. */
  private JsonNode create(Map<String, Object> request) throws InterruptedException {
    var job = finished(request);
    assertEquals("DONE", job.get("status").asString(), () -> job.toString());
    return job.get("created");
  }

  private JsonNode finished(Map<String, Object> request) throws InterruptedException {
    var started = http.post().uri("/api/import").body(request).retrieve().toEntity(JsonNode.class);
    assertEquals(202, started.getStatusCode().value());
    String job = started.getBody().get("job").asString();
    for (int attempt = 0; attempt < 300; attempt++) {
      var status = http.get().uri("/api/import/" + job).retrieve().body(JsonNode.class);
      if (!"RUNNING".equals(status.get("status").asString())) return status;
      Thread.sleep(100);
    }
    throw new AssertionError("Import " + job + " did not finish");
  }

  @Test
  void videoKeepsOnlyTheChosenAudioTrackAndGetsAnEmptyDocument() throws Exception {
    assumeTrue(installed(), "ffmpeg is not installed");
    Path source = root.resolve("film.mkv");
    try (var media = getClass().getResourceAsStream("/media/aac-and-ac3.mkv")) {
      Files.copy(media, source);
    }
    var inspection =
        http.post()
            .uri("/api/import/inspect")
            .body(Map.of("path", source.toString()))
            .retrieve()
            .body(JsonNode.class);
    assertEquals("video", inspection.get("kind").asString());
    assertEquals(2, inspection.get("audioTracks").size());
    assertFalse(inspection.get("audioTracks").get(1).get("playable").asBoolean());

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            create(
                Map.of(
                    "path",
                    source.toString(),
                    "repositoryId",
                    repositoryId,
                    "folder",
                    "Films/My Film",
                    "title",
                    "My film")));
    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            finished(
                Map.of(
                    "path",
                    source.toString(),
                    "repositoryId",
                    repositoryId,
                    "folder",
                    "Films/My Film",
                    "title",
                    "My film",
                    "audioTrack",
                    9)));
    var created =
        create(
            Map.of(
                "path",
                source.toString(),
                "repositoryId",
                repositoryId,
                "folder",
                "Films/My Film",
                "title",
                "My film",
                "audioTrack",
                1));

    Path folder = repository.resolve("Films/My Film");
    var tracks = new Ffmpeg(mapper).audioTracks(folder.resolve("video.mp4"));
    assertEquals(1, tracks.size());
    assertEquals("aac", tracks.getFirst().codec());
    assertTrue(Files.exists(source));
    assertEquals("Films/My Film/document.json", created.get("documentPath").asString());
    var document =
        http.get()
            .uri("/api/titles/" + created.get("titleId").asString())
            .retrieve()
            .body(StudyDocument.class);
    assertEquals("my-film", document.id());
    assertEquals("My film", document.title());
    assertEquals("fr", document.sourceLanguage());
    assertEquals(0, document.video().cues().size());
    var reference = document.video().reference();
    Path video = folder.resolve("video.mp4");
    assertEquals(MediaFingerprint.of(video), reference.fingerprint());
    assertEquals(Files.size(video), reference.sizeBytes());
    assertEquals(new Ffmpeg(mapper).duration(video), reference.durationSeconds());

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            create(
                Map.of(
                    "path",
                    source.toString(),
                    "repositoryId",
                    repositoryId,
                    "folder",
                    "Films/My Film",
                    "title",
                    "Again",
                    "audioTrack",
                    0)));
  }

  @Test
  void delayedAudioIsPaddedSoBrowsersPlayItInSync() throws Exception {
    assumeTrue(installed(), "ffmpeg is not installed");
    Path source = root.resolve("delayed.mp4");
    var generate =
        new ProcessBuilder(
                "ffmpeg",
                "-v",
                "error",
                "-f",
                "lavfi",
                "-i",
                "color=c=black:s=64x64:d=2:r=25",
                "-itsoffset",
                "0.5",
                "-f",
                "lavfi",
                "-t",
                "1.5",
                "-i",
                "anullsrc=r=48000:cl=mono",
                "-map",
                "0:v",
                "-map",
                "1:a",
                "-c:v",
                "libx264",
                "-c:a",
                "aac",
                source.toString())
            .inheritIO()
            .start();
    assertEquals(0, generate.waitFor());
    var created =
        create(
            Map.of(
                "path",
                source.toString(),
                "repositoryId",
                repositoryId,
                "folder",
                "Delayed",
                "title",
                "Delayed"));
    assertNotNull(created.get("titleId"));
    var probe =
        new ProcessBuilder(
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "stream=start_time",
                "-of",
                "csv=p=0",
                repository.resolve("Delayed/video.mp4").toString())
            .start();
    var starts = new String(probe.getInputStream().readAllBytes()).strip().lines().toList();
    assertEquals(List.of("0.000000", "0.000000"), starts);
  }

  @Test
  void comicPagesAreListedInNaturalOrder() throws Exception {
    Path source = root.resolve("Volume 1.cbz");
    try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
      for (String name : List.of("page-10.png", "page-2.png", "page-1.png", "info.txt")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(new byte[] {1});
        zip.closeEntry();
      }
    }
    var created =
        create(
            Map.of(
                "path",
                source.toString(),
                "repositoryId",
                repositoryId,
                "folder",
                "Manga/Volume 1",
                "title",
                "Volume 1"));
    assertTrue(Files.exists(repository.resolve("Manga/Volume 1/Volume 1.cbz")));
    var document =
        http.get()
            .uri("/api/titles/" + created.get("titleId").asString())
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(
        List.of("page-1.png", "page-2.png", "page-10.png"),
        document.comic().pages().stream().map(p -> p.entry()).toList());
    assertEquals(ReadingDirection.RTL, document.comic().readingDirection());
    Path cbz = repository.resolve("Manga/Volume 1/Volume 1.cbz");
    assertEquals(
        new ComicReference(MediaFingerprint.of(cbz), Files.size(cbz)),
        document.comic().reference());
  }

  @Test
  void failedWritesAreReportedByTheJob() throws Exception {
    Path source = root.resolve("Volume 1.cbz");
    try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
      zip.putNextEntry(new ZipEntry("page-1.png"));
      zip.write(new byte[] {1});
      zip.closeEntry();
    }
    Files.writeString(repository.resolve("Taken"), "a file where the title folder would go");
    var job =
        finished(
            Map.of(
                "path",
                source.toString(),
                "repositoryId",
                repositoryId,
                "folder",
                "Taken",
                "title",
                "Taken"));
    assertEquals("FAILED", job.get("status").asString());
    assertTrue(job.get("error").asString().contains("Taken"), job.toString());
    assertThrows(
        HttpClientErrorException.NotFound.class,
        () -> http.get().uri("/api/import/unknown").retrieve().body(JsonNode.class));
  }

  @Test
  void foldersMustStayInsideTheRepository() {
    for (String folder : List.of("../outside", "/absolute", "a/../b", ""))
      assertThrows(
          HttpClientErrorException.BadRequest.class,
          () ->
              create(
                  Map.of(
                      "path",
                      root.resolve("video.mp4").toString(),
                      "repositoryId",
                      repositoryId,
                      "folder",
                      folder,
                      "title",
                      "Title")));
  }

  private static boolean installed() {
    try {
      return new ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0;
    } catch (Exception missing) {
      return false;
    }
  }
}
