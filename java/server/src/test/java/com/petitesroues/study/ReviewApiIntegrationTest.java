package com.petitesroues.study;

import static com.petitesroues.support.DocumentFixtures.document;
import static com.petitesroues.support.TestDocuments.edit;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/** The player queues edits over HTTP; an agent writes them into the file through a batch. */
class ReviewApiIntegrationTest extends HttpIntegrationTest {
  @Test
  void agentWritesAQueuedEditThroughABatch() throws Exception {
    var title = document("review-api");
    registerTitle(title);
    var file = documentPath("review-api");
    byte[] original = Files.readAllBytes(file);
    String api = "/api/titles/review-api";

    var cue = title.video().cues().get(0).withStudy(s -> s.withSource("Bonsoir !"));
    http.put().uri(api + "/cues/stable-cue").body(edit(cue)).retrieve().toBodilessEntity();
    assertArrayEquals(original, Files.readAllBytes(file));
    var served = http.get().uri(api).retrieve().body(StudyDocument.class);
    assertEquals("Bonsoir !", served.video().cues().get(0).study().source());

    var queue = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertEquals("pending", queue.get(0).get("state").asString());
    assertEquals("edit-video-cue", queue.get(0).get("command").get("type").asString());
    assertEquals(
        "review-api",
        http.get()
            .uri("/api/review/pending")
            .retrieve()
            .body(JsonNode.class)
            .get(0)
            .get("titleId")
            .asString());

    var batch = http.post().uri(api + "/review/batches").retrieve().body(JsonNode.class);
    var document =
        new StudyDocumentCodec()
            .decode(batch.get("document").toString().getBytes(StandardCharsets.UTF_8));
    assertEquals(served, document);
    String complete = api + "/review/batches/" + batch.get("batch").asLong() + "/complete";
    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () -> http.post().uri(complete).body(Map.of()).retrieve().toBodilessEntity());

    var agent =
        document.withVideo(
            v -> v.withCue(0, c -> c.withStudy(s -> s.withTranslation("Good evening!"))));
    Files.write(file, new StudyDocumentCodec().encode(agent).getBytes(StandardCharsets.UTF_8));
    http.post().uri(complete).body(Map.of("outcome", "applied")).retrieve().toBodilessEntity();

    assertEquals(agent, http.get().uri(api).retrieve().body(StudyDocument.class));
    assertEquals(
        204,
        http.post()
            .uri(api + "/review/batches")
            .retrieve()
            .toBodilessEntity()
            .getStatusCode()
            .value());
    var history = http.get().uri(api + "/review-history").retrieve().body(JsonNode.class);
    assertEquals("applied", history.get(0).get("outcome").asString());
  }

  @Test
  void reviewRequestsQueueAllOrNoneAndCompleteWithoutAFileChange() throws Exception {
    registerTitle(document("review-requests"));
    String api = "/api/titles/review-requests";
    byte[] original = Files.readAllBytes(documentPath("review-requests"));

    assertThrows(
        HttpClientErrorException.class,
        () ->
            http.post()
                .uri(api + "/review/requests")
                .body(
                    List.of(
                        Map.of("cueId", "stable-cue", "text", "First pass"),
                        Map.of("cueId", "missing", "text", "First pass")))
                .retrieve()
                .toBodilessEntity());
    assertTrue(http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class).isEmpty());

    var queued =
        http.post()
            .uri(api + "/review/requests")
            .body(List.of(Map.of("cueId", "stable-cue", "text", "First-pass notes from luna")))
            .retrieve()
            .body(JsonNode.class);
    assertEquals(1, queued.get("queued").asInt());
    var batch = http.post().uri(api + "/review/batches").retrieve().body(JsonNode.class);
    var change = batch.get("changes").get(0);
    assertEquals("review-request", change.get("kind").asString());
    assertEquals("First-pass notes from luna", change.get("suggestion").asString());
    http.post()
        .uri(api + "/review/batches/" + batch.get("batch").asLong() + "/complete")
        .body(Map.of("outcome", "reviewed"))
        .retrieve()
        .toBodilessEntity();
    assertArrayEquals(original, Files.readAllBytes(documentPath("review-requests")));
  }
}
