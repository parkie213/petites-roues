package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiperSynthesizerTest {
  @TempDir Path temp;

  @Test
  void choosesTheFirstMatchingModelWithALanguageBoundary() throws Exception {
    Files.createFile(temp.resolve("fra_FR-wrong.onnx"));
    Files.createFile(temp.resolve("fr_FR-b.onnx"));
    Files.createFile(temp.resolve("fr_FR-a.onnx"));
    Files.writeString(temp.resolve("fr_FR-a.onnx.json"), "{}");
    Files.writeString(temp.resolve("fr_FR-b.onnx.json"), "{}");
    try (var engine = new PiperSynthesizer(temp)) {
      assertEquals("fr_FR-a.onnx", engine.installedVoices().get("fr").getFirst());
      assertFalse(engine.installedVoices().containsKey("ja"));
    }
  }

  @Test
  void listsOnlyInstalledPairsAndRejectsPathsAndMissingSelectionsBeforeNativeLoading()
      throws Exception {
    Files.writeString(temp.resolve("fr_FR-b.onnx"), "");
    Files.writeString(temp.resolve("fr_FR-b.onnx.json"), "{}");
    Files.writeString(temp.resolve("en_GB-a.onnx"), "");
    Files.writeString(temp.resolve("en_GB-a.onnx.json"), "{}");
    Files.writeString(temp.resolve("fr_FR-incomplete.onnx"), "");
    try (var engine = new PiperSynthesizer(temp)) {
      assertEquals(List.of("fr_FR-b.onnx"), engine.installedVoices().get("fr"));
      assertEquals(2, engine.installedVoices().size());
      assertThrows(
          SpeechUnavailableException.class,
          () -> engine.synthesize("fr", "../outside.onnx", "bonjour"));
      assertThrows(
          SpeechUnavailableException.class,
          () -> engine.synthesize("fr", "en_GB-a.onnx", "bonjour"));
      assertThrows(
          SpeechUnavailableException.class,
          () -> engine.synthesize("fr", "fr_FR-missing.onnx", "bonjour"));
    }
  }

  @Test
  void missingModelsAndConfigurationRemainOptionalAndClosePreventsNewLoads() throws Exception {
    var engine = new PiperSynthesizer(temp);
    assertThrows(SpeechUnavailableException.class, () -> engine.synthesize("fr", "bonjour"));
    Files.createFile(temp.resolve("fr_FR-test.onnx"));
    var failure =
        assertThrows(SpeechUnavailableException.class, () -> engine.synthesize("fr", "bonjour"));
    assertTrue(failure.getMessage().contains("voice"));
    engine.close();
    engine.close();
    assertThrows(IllegalStateException.class, () -> engine.synthesize("fr", "bonjour"));
  }
}
