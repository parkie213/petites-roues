package app.petitesroues;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.HttpClients.client;
import static app.petitesroues.support.HttpClients.command;
import static app.petitesroues.support.HttpClients.discover;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoReference;
import app.petitesroues.model.library.Library;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * One user journey: register a media folder (its video warns that no study document matches), add a
 * folder of study documents, see the video linked by fingerprint, play it and resume.
 */
class ApplicationSmokeTest extends HttpIntegrationTest {
  @Test
  void registerMediaThenDocumentsLinkByFingerprintAndResumePlayback() throws Exception {
    Path media = Files.createDirectories(root.resolve("media/series"));
    Path video = Files.copy(root.resolve("video.mp4"), media.resolve("Episode 1.mp4"));
    discover(
        http,
        "add",
        null,
        Map.of("localPath", root.resolve("media").toString()),
        app.petitesroues.model.library.ContentRepository.class);
    var unmatched = http.get().uri("/api/library").retrieve().body(Library.class);
    var mediaFolder = unmatched.repositories().getFirst();
    assertTrue(mediaFolder.entries().isEmpty());
    assertEquals("series/Episode 1.mp4", mediaFolder.media().getFirst().relativePath());
    assertEquals(List.of("No study document"), mediaFolder.media().getFirst().warnings());

    Path repository = root.resolve("content");
    Path titleFolder = repository.resolve("series/season-01");
    Files.createDirectories(titleFolder);
    var reference = new VideoReference(MediaFingerprint.of(video), null, null);
    var title = document("episode-01").withVideo(body -> body.withReference(reference));
    Files.write(
        titleFolder.resolve("episode-01.json"),
        new StudyDocumentCodec().encode(title).getBytes(StandardCharsets.UTF_8));

    var registered =
        discover(http, "add", null, Map.of("localPath", repository.toString()), JsonNode.class);

    // Each folder lists what it holds: the documents folder its title, the media folder its video,
    // which now names that title and no longer warns.
    var library = http.get().uri("/api/library").retrieve().body(Library.class);
    var selected =
        library.repositories().stream()
            .filter(folder -> folder.repository().id().equals(registered.get("id").asText()))
            .findFirst()
            .orElseThrow()
            .entries()
            .getFirst();
    assertEquals("series/Episode 1.mp4", selected.mediaPath());
    assertEquals("series/season-01/episode-01.json", selected.relativePath());
    assertTrue(selected.videoAvailable());
    var indexed =
        library.repositories().stream()
            .filter(folder -> folder.repository().name().equals("media"))
            .findFirst()
            .orElseThrow()
            .media()
            .getFirst();
    assertEquals(selected.id(), indexed.titleId());
    assertTrue(indexed.warnings().isEmpty(), "the linked video no longer warns");
    String titleApi = "/api/titles/" + selected.id();

    var wire = http.get().uri(titleApi).accept(MediaType.ALL).retrieve().toEntity(String.class);
    assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(wire.getHeaders().getContentType()));
    assertTrue(mapper.readTree(wire.getBody()).path("video").path("cues").get(0).has("line"));
    var loaded = http.get().uri(titleApi).retrieve().body(StudyDocument.class);
    assertEquals(title.id(), loaded.id());
    assertEquals("Bonjour !", loaded.video().cues().getFirst().line().source());
    // The media URL redirects to one carrying the file's version; old versions redirect onward.
    var redirect = http.get().uri(titleApi + "/media").retrieve().toBodilessEntity();
    assertEquals(302, redirect.getStatusCode().value());
    String versioned = titleApi + "/" + redirect.getHeaders().getLocation();
    assertArrayEquals(
        Files.readAllBytes(video), http.get().uri(versioned).retrieve().body(byte[].class));
    var stale = http.get().uri(titleApi + "/media/1-1").retrieve().toBodilessEntity();
    assertEquals(
        versioned.substring(versioned.lastIndexOf('/') + 1),
        stale.getHeaders().getLocation().toString());
    // Any indexed video plays by fingerprint, before or without a study document.
    String byFingerprint = "/api/media/" + MediaFingerprint.of(video) + "/video";
    var fingerprinted = http.get().uri(byFingerprint).retrieve().toBodilessEntity();
    assertArrayEquals(
        Files.readAllBytes(video),
        http.get()
            .uri(
                "/api/media/"
                    + MediaFingerprint.of(video)
                    + "/"
                    + fingerprinted.getHeaders().getLocation())
            .retrieve()
            .body(byte[].class));

    // Pausing saves the position; reopening the title reads it through a fresh client.
    http.put()
        .uri(titleApi + "/progress")
        .body(Map.of("positionSeconds", 0.5))
        .retrieve()
        .toBodilessEntity();
    var reopened = client("http://127.0.0.1:" + port);
    assertEquals(
        title.id(), reopened.get().uri(titleApi).retrieve().body(StudyDocument.class).id());
    var progress = reopened.get().uri(titleApi + "/progress").retrieve().body(JsonNode.class);
    assertEquals(0.5, progress.get("positionSeconds").asDouble());

    // Clearing from the library card forgets the position, so the title opens at the start.
    http.delete().uri(titleApi + "/progress").retrieve().toBodilessEntity();
    progress = http.get().uri(titleApi + "/progress").retrieve().body(JsonNode.class);
    assertEquals(0, progress.get("positionSeconds").asDouble());
    assertTrue(progress.get("updatedAt").isNull());
  }

  @Test
  void editingCommandsCannotReplaceIdentityAndRequireTiming() throws Exception {
    registerTitle(document("edit-command"));
    String api = "/api/titles/edit-command";
    var edited =
        command(
                http,
                api,
                "edit-video-cue",
                "stable-cue",
                Map.of(
                    "source",
                    "Salut !",
                    "start",
                    0.1,
                    "end",
                    0.9,
                    "notes",
                    List.of(),
                    "id",
                    "client-selected-id",
                    "alignment",
                    Map.of()))
            .retrieve()
            .body(StudyDocument.class);
    var cue = edited.video().cues().get(0);
    assertEquals("stable-cue", cue.line().id());
    assertFalse(cue.line().alignment() != null);
    assertEquals(
        400,
        command(http, api, "edit-video-cue", "stable-cue", Map.of("source", "Missing timing"))
            .<Integer>exchange((request, response) -> response.getStatusCode().value())
            .intValue());
    assertEquals(edited, http.get().uri(api).retrieve().body(StudyDocument.class));
  }

  @Test
  void validateReportsEveryProblemInACandidateWithoutWritingIt() throws Exception {
    registerTitle(document("validate-me"));
    String api = "/api/titles/validate-me";
    var codec = new StudyDocumentCodec();
    var current = http.get().uri(api).retrieve().body(StudyDocument.class);
    var file = codec.encode(current);

    assertEquals(List.of(), problems(api, file));
    var broken = current.withVideo(v -> v.withCue(0, c -> c.withTiming(c.start(), -1.0)));
    var renamed = current.withId("other");
    String brokenJson = mapper.writeValueAsString(broken);
    assertEquals(
        List.of("stable-cue: Invalid timing for cue stable-cue"), problems(api, brokenJson));
    assertEquals(
        List.of("Document ID must stay validate-me, not other"),
        problems(api, codec.encode(renamed)));
    assertEquals(1, problems(api, "{\"id\":\"validate-me\",\"audioVerified\":true}").size());
    assertEquals(current, http.get().uri(api).retrieve().body(StudyDocument.class));
  }

  private List<String> problems(String api, String candidate) {
    var body =
        http.post()
            .uri(api + "/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(candidate.getBytes(StandardCharsets.UTF_8))
            .retrieve()
            .body(JsonNode.class);
    return body.path("problems").valueStream().map(JsonNode::asString).toList();
  }
}
