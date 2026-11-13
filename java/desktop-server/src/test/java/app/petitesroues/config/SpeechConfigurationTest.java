package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.speech.PiperSynthesizer;
import app.petitesroues.speech.SpeechUnavailableException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SpeechConfigurationTest {
  @TempDir Path temp;

  @Test
  void missingInstallationDoesNotBlockStartupAndSpringClosesTheSynthesizer() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "speech-test", Map.of("petitesroues.speech.voices", temp.toString())));
      context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().build());
      context.register(SpeechConfiguration.class);
      context.refresh();
      var synthesizer = context.getBean(PiperSynthesizer.class);
      assertThrows(SpeechUnavailableException.class, () -> synthesizer.synthesize("fr", "bonjour"));
      context.close();
      assertThrows(IllegalStateException.class, () -> synthesizer.synthesize("fr", "bonjour"));
    }
  }
}
