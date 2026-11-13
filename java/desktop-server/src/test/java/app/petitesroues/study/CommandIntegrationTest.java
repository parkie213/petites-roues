package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.support.HttpIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies atomic batches, durable retry identity and stale-state rejection through HTTP. */
class CommandIntegrationTest extends HttpIntegrationTest {
  @Test
  void retriesReturnTheOriginalIdentitiesAndMixedBatchesRollback() throws Exception {
    registerTitle(document("commands"));
    String api = "/api/titles/commands";
    var version =
        http.get().uri(api).retrieve().toBodilessEntity().getHeaders().getFirst("X-State-Version");
    var add =
        Map.of("type", "add-video-cue", "edit", Map.of("start", 2, "end", 3, "source", "Salut !"));
    var request =
        Map.of("requestId", "retry-add", "expectedVersion", version, "commands", List.of(add));
    var first =
        http.post().uri(api + "/commands").body(request).retrieve().toEntity(StudyDocument.class);
    var retry =
        http.post().uri(api + "/commands").body(request).retrieve().toEntity(StudyDocument.class);
    assertEquals(first.getBody(), retry.getBody());
    assertEquals(
        first.getHeaders().getFirst("X-Created-Ids"), retry.getHeaders().getFirst("X-Created-Ids"));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM review_queue", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM command_receipts", Integer.class));
    assertEquals(
        409,
        http.post()
            .uri(api + "/commands")
            .body(
                Map.of("requestId", "stale", "expectedVersion", version, "commands", List.of(add)))
            .<Integer>exchange((r, s) -> s.getStatusCode().value()));
    var current = first.getHeaders().getFirst("X-State-Version");
    var failed =
        Map.of(
            "requestId",
            "rollback",
            "expectedVersion",
            current,
            "commands",
            List.of(add, Map.of("type", "delete-video-cue", "cueId", "missing")));
    assertEquals(
        404,
        http.post()
            .uri(api + "/commands")
            .body(failed)
            .<Integer>exchange((r, s) -> s.getStatusCode().value()));
    assertEquals(first.getBody(), http.get().uri(api).retrieve().body(StudyDocument.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM review_queue", Integer.class));
    assertEquals(
        409,
        http.post()
            .uri(api + "/commands")
            .body(
                Map.of(
                    "requestId", "retry-add", "expectedVersion", current, "commands", List.of(add)))
            .<Integer>exchange((r, s) -> s.getStatusCode().value()));
    assertFalse(
        jdbc.queryForObject(
                "SELECT result_json FROM command_receipts WHERE request_id='retry-add'",
                String.class)
            .contains("document"));
    var later =
        http.post()
            .uri(api + "/commands")
            .body(
                Map.of(
                    "requestId",
                    "later-comment",
                    "expectedVersion",
                    current,
                    "commands",
                    List.of(
                        Map.of(
                            "type", "comment", "cueId", "stable-cue", "text", "Later feedback"))))
            .retrieve()
            .toEntity(StudyDocument.class);
    var replay =
        http.post().uri(api + "/commands").body(request).retrieve().toEntity(StudyDocument.class);
    assertEquals(later.getBody(), replay.getBody());
    assertEquals(
        later.getHeaders().getFirst("X-State-Version"),
        replay.getHeaders().getFirst("X-State-Version"));
    assertEquals(
        first.getHeaders().getFirst("X-Accepted-Version"),
        replay.getHeaders().getFirst("X-Accepted-Version"));
    assertEquals(
        first.getHeaders().getFirst("X-Created-Ids"),
        replay.getHeaders().getFirst("X-Created-Ids"));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM review_queue", Integer.class));
  }
}
