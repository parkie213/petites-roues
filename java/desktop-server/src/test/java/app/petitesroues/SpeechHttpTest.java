package app.petitesroues;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.support.HttpIntegrationTest;
import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Exercises the unchanged speech HTTP contract with the actual bundled native runtime. */
@EnabledIfSystemProperty(named = "petitesroues.test.voices", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SpeechHttpTest extends HttpIntegrationTest {
  @DynamicPropertySource
  static void voices(DynamicPropertyRegistry properties) {
    properties.add(
        "petitesroues.speech.voices", () -> System.getProperty("petitesroues.test.voices"));
  }

  @Test
  void returnsPlayableWavFromTheDocumentsSourceLanguage() throws Exception {
    registerTitle(document("speech"));
    var response =
        http.get().uri("/api/titles/speech/speech?text=BONJOUR").retrieve().toEntity(byte[].class);
    assertEquals("audio/wav", response.getHeaders().getContentType().toString());
    assertEquals("no-store", response.getHeaders().getCacheControl());
    try (var audio =
        AudioSystem.getAudioInputStream(new ByteArrayInputStream(response.getBody()))) {
      assertEquals(22050, audio.getFormat().getSampleRate());
      assertTrue(audio.getFrameLength() > 2205);
    }
    var before = http.get().uri("/api/speech/voices").retrieve().body(String.class);
    var preview =
        http.get()
            .uri("/api/speech/voices/fr/preview?text=Bonjour")
            .retrieve()
            .toEntity(byte[].class);
    assertEquals("audio/wav", preview.getHeaders().getContentType().toString());
    try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(preview.getBody()))) {
      assertTrue(audio.getFrameLength() > 2205);
    }
    assertEquals(before, http.get().uri("/api/speech/voices").retrieve().body(String.class));
    assertEquals(
        400,
        http.get()
            .uri("/api/titles/speech/speech?text=")
            .<Integer>exchange((request, result) -> result.getStatusCode().value())
            .intValue());
  }
}
