package app.petitesroues;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.support.HttpIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/** Preferences use real HTTP and SQLite; metadata-only files must never trigger native loading. */
class SpeechVoicesHttpTest extends HttpIntegrationTest {
  @TempDir static Path voices;

  @DynamicPropertySource
  static void voices(DynamicPropertyRegistry properties) {
    properties.add("petitesroues.speech.voices", () -> voices.toString());
  }

  @Test
  void listsPairsPersistsSelectionAndRestoresAutomaticChoice() throws Exception {
    for (String voice : List.of("fr_FR-a.onnx", "fr_FR-b.onnx")) {
      Files.writeString(voices.resolve(voice), "");
      Files.writeString(voices.resolve(voice + ".json"), "{}");
    }
    var installed = http.get().uri("/api/speech/voices").retrieve().body(JsonNode.class);
    assertEquals(2, installed.get(0).path("voices").size());
    assertTrue(installed.get(0).path("selectedVoice").isNull());
    var saved =
        http.put()
            .uri("/api/speech/voices/fr")
            .body(Map.of("voice", "fr_FR-b.onnx"))
            .retrieve()
            .body(JsonNode.class);
    assertEquals("fr_FR-b.onnx", saved.get(0).path("selectedVoice").asString());
    assertEquals(saved, http.get().uri("/api/speech/voices").retrieve().body(JsonNode.class));
    assertEquals(
        "fr_FR-b.onnx",
        jdbc.queryForObject(
            "SELECT value FROM settings WHERE key='speech.voice.fr'", String.class));
    int rejected =
        http.put()
            .uri("/api/speech/voices/fr")
            .body(Map.of("voice", "../outside.onnx"))
            .<Integer>exchange((request, response) -> response.getStatusCode().value());
    assertEquals(400, rejected);
    assertEquals(saved, http.get().uri("/api/speech/voices").retrieve().body(JsonNode.class));
    Files.delete(voices.resolve("fr_FR-b.onnx"));
    var missing = http.get().uri("/api/speech/voices").retrieve().body(JsonNode.class);
    assertEquals("fr_FR-b.onnx", missing.get(0).path("selectedVoice").asString());
    assertEquals(1, missing.get(0).path("voices").size());
    http.put().uri("/api/speech/voices/fr").body(Map.of()).retrieve().toBodilessEntity();
    assertTrue(
        http.get()
            .uri("/api/speech/voices")
            .retrieve()
            .body(JsonNode.class)
            .get(0)
            .path("selectedVoice")
            .isNull());
  }
}
