package app.petitesroues.config;

import app.petitesroues.speech.PiperSynthesizer;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures optional local speech; Spring closes the loaded native voices at shutdown. */
@Configuration(proxyBeanMethods = false)
public class SpeechConfiguration {
  /** Voice models are external data; Piper's native runtime is bundled in its dependency. */
  @Bean
  public PiperSynthesizer piperSynthesizer(
      @Value("${petitesroues.speech.voices:${user.home}/.local/share/piper/voices}")
          String voices) {
    return new PiperSynthesizer(Path.of(voices));
  }
}
