package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Runs with real bundled natives and an externally installed French model, including in CI. */
@EnabledIfSystemProperty(named = "petitesroues.test.voices", matches = ".+")
class PiperNativeTest {
  @Test
  void synthesizesRepeatedUtterancesAndReopensAfterClosing() throws Exception {
    Path voices = Path.of(System.getProperty("petitesroues.test.voices"));
    var engine = new PiperSynthesizer(voices);
    try (engine) {
      String selected = engine.installedVoices().get("fr").getFirst();
      assertAudio(engine.synthesize("fr", selected, "Bonjour ! Comment allez-vous ?"));
      assertAudio(engine.synthesize("fr", "Il a dit : « Ça va ? »\nOui, très bien."));
    }
    assertThrows(IllegalStateException.class, () -> engine.synthesize("fr", "encore"));
    try (var reopened = new PiperSynthesizer(voices)) {
      assertAudio(reopened.synthesize("fr", "À bientôt."));
    }
  }

  private static void assertAudio(byte[] wav) throws Exception {
    try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
      assertEquals(1, audio.getFormat().getChannels());
      assertEquals(16, audio.getFormat().getSampleSizeInBits());
      assertEquals(22050, audio.getFormat().getSampleRate());
      assertTrue(audio.getFrameLength() > 2205, "At least 100ms of speech");
      byte[] pcm = audio.readAllBytes();
      boolean audible = false;
      for (byte sample : pcm) audible |= sample != 0;
      assertTrue(audible, "Speech must contain non-silent PCM samples");
    }
  }
}
