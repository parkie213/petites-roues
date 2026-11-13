package app.petitesroues.config;

import app.petitesroues.speech.PiperSynthesizer;
import app.petitesroues.speech.VoiceCatalogueService;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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

  /** Spring owns the shared client's shutdown; redirects are required by Hugging Face downloads. */
  @Bean
  public HttpClient speechDownloads() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /** Provider locations are configurable for offline integration tests and alternative mirrors. */
  @Bean
  public VoiceCatalogueService voiceCatalogueService(
      @Value("${petitesroues.speech.voices:${user.home}/.local/share/piper/voices}") String voices,
      @Qualifier("speechDownloads") HttpClient http,
      ObjectMapper mapper,
      @Value(
              "${petitesroues.speech.catalogue:https://huggingface.co/rhasspy/piper-voices/raw/main/voices.json}")
          URI catalogue,
      @Value(
              "${petitesroues.speech.repository:https://huggingface.co/rhasspy/piper-voices/resolve/main/}")
          URI repository,
      @Value("${petitesroues.speech.samples:https://rhasspy.github.io/piper-samples/samples/}")
          URI samples) {
    return new VoiceCatalogueService(Path.of(voices), http, mapper, catalogue, repository, samples);
  }
}
