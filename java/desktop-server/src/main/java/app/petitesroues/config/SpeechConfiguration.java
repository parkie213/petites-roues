package app.petitesroues.config;

import app.petitesroues.speech.PiperSynthesizer;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/** Configures the optional Piper adapter; Spring closes its owned processes at shutdown. */
@Configuration(proxyBeanMethods = false)
public class SpeechConfiguration {
  /** Compact JSON is a Piper protocol requirement, independent of HTTP pretty-print settings. */
  @Bean
  public PiperSynthesizer piperSynthesizer(
      ObjectMapper mapper,
      @Value("${petitesroues.speech.piper:${user.home}/.local/share/piper/piper/piper}")
          String executable,
      @Value("${petitesroues.speech.voices:${user.home}/.local/share/piper/voices}")
          String voices) {
    return new PiperSynthesizer(
        Path.of(executable),
        Path.of(voices),
        mapper.writer().without(SerializationFeature.INDENT_OUTPUT));
  }
}
