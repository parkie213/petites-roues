package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Runs with real bundled natives and an externally installed French model, including in CI. */
@EnabledIfSystemProperty(named = "petitesroues.test.voices", matches = ".+")
class PiperNativeTest {
  @TempDir Path temp;

  @Test
  void switchesVoicesAndHandlesConcurrentRequestsWithRealNativeModels() throws Exception {
    Path voices = Path.of(System.getProperty("petitesroues.test.voices"));
    String installed;
    try (var engine = new PiperSynthesizer(voices)) {
      installed = engine.installedVoices().get("fr").getFirst();
    }
    for (String name : new String[] {"fr_FR-a.onnx", "fr_FR-b.onnx"}) {
      Files.createSymbolicLink(temp.resolve(name), voices.resolve(installed).toAbsolutePath());
      Files.createSymbolicLink(
          temp.resolve(name + ".json"), voices.resolve(installed + ".json").toAbsolutePath());
    }
    try (var engine = new PiperSynthesizer(temp);
        var requests = Executors.newVirtualThreadPerTaskExecutor()) {
      assertAudio(engine.synthesize("fr", "fr_FR-a.onnx", "Bonjour."));
      assertAudio(engine.synthesize("fr", "fr_FR-b.onnx", "Salut."));
      var first = requests.submit(() -> engine.synthesize("fr", "fr_FR-a.onnx", "À bientôt."));
      var second = requests.submit(() -> engine.synthesize("fr", "fr_FR-b.onnx", "Au revoir."));
      assertAudio(first.get());
      assertAudio(second.get());
    }
  }

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
