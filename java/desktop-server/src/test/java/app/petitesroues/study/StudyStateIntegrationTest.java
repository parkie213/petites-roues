package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.HttpClients.command;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.support.HttpIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Verifies application state is coherent and never leaks into raw/downloaded portable JSON. */
class StudyStateIntegrationTest extends HttpIntegrationTest {
  @Test
  void openingStateIncludesCommittedFeedbackAndProgressWhileDownloadsStayPortable()
      throws Exception {
    registerTitle(document("study-state"));
    String api = "/api/titles/study-state";
    command(http, api, "comment", "stable-cue", Map.of("text", "Please check"))
        .retrieve()
        .toBodilessEntity();
    http.put()
        .uri(api + "/progress")
        .body(Map.of("positionSeconds", 2.5))
        .retrieve()
        .toBodilessEntity();
    var response = http.get().uri(api + "?view=study").retrieve().toEntity(JsonNode.class);
    var state = response.getBody();
    assertEquals(1, state.path("pending").size());
    assertEquals(2.5, state.path("progress").path("positionSeconds").asDouble());
    assertEquals(
        response.getHeaders().getFirst("X-State-Version"), state.path("version").asString());
    var raw = http.get().uri(api).retrieve().body(JsonNode.class);
    assertEquals(raw, state.path("document"));
    var download = http.get().uri(api + "?download=true").retrieve().toEntity(JsonNode.class);
    assertEquals(raw, download.getBody());
    assertTrue(download.getHeaders().getFirst("Content-Disposition").contains("study.json"));
    assertFalse(raw.has("pending"));
    assertFalse(raw.has("progress"));
    assertFalse(raw.has("version"));
    for (String retired : List.of("/comic", "/export"))
      assertEquals(
          404,
          http.get().uri(api + retired).<Integer>exchange((r, s) -> s.getStatusCode().value()));
    assertEquals(
        400,
        http.get()
            .uri(api + "?view=unknown")
            .<Integer>exchange((r, s) -> s.getStatusCode().value()));
    assertEquals(
        400,
        http.get()
            .uri(api + "?view=study&download=true")
            .<Integer>exchange((r, s) -> s.getStatusCode().value()));
  }
}
