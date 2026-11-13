package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.support.HttpIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Checks position shape and media-kind rejection at the shared progress boundary. */
class ProgressIntegrationTest extends HttpIntegrationTest {
  @Test
  void rejectsAmbiguousAndWrongKindPositionsWithoutChangingSavedState() throws Exception {
    registerTitle(document("positions"));
    String api = "/api/titles/positions/progress";
    http.put().uri(api).body(Map.of("positionSeconds", 3.5)).retrieve().toBodilessEntity();
    for (var invalid :
        java.util.List.of(
            Map.of("positionSeconds", 1, "pageId", "cover"),
            Map.of("pageId", "cover"),
            Map.of("positionSeconds", -1))) {
      assertEquals(
          400,
          http.put().uri(api).body(invalid).<Integer>exchange((r, s) -> s.getStatusCode().value()));
      assertEquals(
          3.5,
          http.get().uri(api).retrieve().body(JsonNode.class).path("positionSeconds").asDouble());
    }
    http.delete().uri(api).retrieve().toBodilessEntity();
    assertEquals(
        0, http.get().uri(api).retrieve().body(JsonNode.class).path("positionSeconds").asDouble());
  }
}
