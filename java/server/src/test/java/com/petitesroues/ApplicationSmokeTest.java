package com.petitesroues;

import static com.petitesroues.support.DocumentFixtures.document;
import static com.petitesroues.support.HttpClients.client;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.Library;
import com.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** One user journey through repository browsing, media selection and playback resume. */
class ApplicationSmokeTest extends HttpIntegrationTest {
  @Test
  void registerBrowseSelectMediaAndResumePlayback() throws Exception {
    Path repository = root.resolve("content");
    Path titleFolder = repository.resolve("series/season-01/episode-01");
    Files.createDirectories(titleFolder);
    var title = document("episode-01");
    Files.write(
        titleFolder.resolve("document.json"),
        new StudyDocumentCodec().encode(title).getBytes(StandardCharsets.UTF_8));
    Files.copy(root.resolve("video.mp4"), titleFolder.resolve("video.mp4"));

    var registered =
        http.post()
            .uri("/api/repositories")
            .body(Map.of("localPath", repository.toString()))
            .retrieve()
            .body(JsonNode.class);

    // The folder browser uses these indexed paths to construct its hierarchy.
    var library = http.get().uri("/api/library").retrieve().body(Library.class);
    var content = library.repositories().getFirst();
    assertEquals(registered.get("id").asText(), content.repository().id());
    var selected = content.entries().getFirst();
    assertEquals("series/season-01/episode-01/document.json", selected.relativePath());
    String titleApi = "/api/titles/" + selected.id();

    var wire = http.get().uri(titleApi).accept(MediaType.ALL).retrieve().toEntity(String.class);
    assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(wire.getHeaders().getContentType()));
    assertTrue(mapper.readTree(wire.getBody()).path("video").path("cues").get(0).has("study"));
    var loaded = http.get().uri(titleApi).retrieve().body(StudyDocument.class);
    assertEquals(title.id(), loaded.id());
    assertEquals("Bonjour !", loaded.video().cues().getFirst().study().source());
    // The media URL redirects to one carrying the file's version; old versions redirect onward.
    var redirect = http.get().uri(titleApi + "/media").retrieve().toBodilessEntity();
    assertEquals(302, redirect.getStatusCode().value());
    String versioned = titleApi + "/" + redirect.getHeaders().getLocation();
    assertArrayEquals(
        Files.readAllBytes(titleFolder.resolve("video.mp4")),
        http.get().uri(versioned).retrieve().body(byte[].class));
    var stale = http.get().uri(titleApi + "/media/1-1").retrieve().toBodilessEntity();
    assertEquals(
        versioned.substring(versioned.lastIndexOf('/') + 1),
        stale.getHeaders().getLocation().toString());

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
        http.put()
            .uri(api + "/cues/stable-cue")
            .body(
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
    assertEquals("stable-cue", cue.study().id());
    assertFalse(cue.study().alignment() != null);
    assertTrue(cue.study().alignmentStale());
    assertEquals(
        400,
        http.put()
            .uri(api + "/cues/stable-cue")
            .body(Map.of("source", "Missing timing"))
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
    String brokenJson = new ObjectMapper().writeValueAsString(broken);
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
