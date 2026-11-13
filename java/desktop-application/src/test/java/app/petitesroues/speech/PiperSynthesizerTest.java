package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiperSynthesizerTest {
  @TempDir Path temp;

  @Test
  void choosesTheFirstMatchingModelWithALanguageBoundary() throws Exception {
    Files.createFile(temp.resolve("fra_FR-wrong.onnx"));
    Files.createFile(temp.resolve("fr_FR-b.onnx"));
    Files.createFile(temp.resolve("fr_FR-a.onnx"));
    assertEquals(temp.resolve("fr_FR-a.onnx"), PiperSynthesizer.model(temp, "fr"));
    assertThrows(SpeechUnavailableException.class, () -> PiperSynthesizer.model(temp, "ja"));
  }

  @Test
  void missingModelsAndConfigurationRemainOptionalAndClosePreventsNewLoads() throws Exception {
    var engine = new PiperSynthesizer(temp);
    assertThrows(SpeechUnavailableException.class, () -> engine.synthesize("fr", "bonjour"));
    Files.createFile(temp.resolve("fr_FR-test.onnx"));
    var failure =
        assertThrows(SpeechUnavailableException.class, () -> engine.synthesize("fr", "bonjour"));
    assertTrue(failure.getMessage().contains("configuration"));
    engine.close();
    engine.close();
    assertThrows(IllegalStateException.class, () -> engine.synthesize("fr", "bonjour"));
  }
}
