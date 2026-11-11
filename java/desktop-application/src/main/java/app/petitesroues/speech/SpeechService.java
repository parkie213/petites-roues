package app.petitesroues.speech;

import app.petitesroues.library.LibraryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reads a title's source-language text aloud with a local Piper voice. The voice is chosen from the
 * document's {@code sourceLanguage}: the first model in the voices directory whose file name starts
 * with that language (for example {@code fr_FR-siwis-medium.onnx} for {@code fr}). Each language
 * keeps one Piper process running for the life of the server. Audio is generated for every request
 * and never stored.
 */
@Service
public class SpeechService implements DisposableBean {
  static final int MAX_TEXT_LENGTH = 1000;

  private final LibraryService library;
  private final Path piper;
  private final Path voices;
  private final Map<String, PiperVoice> running = new ConcurrentHashMap<>();

  public SpeechService(
      LibraryService library,
      @Value("${petitesroues.speech.piper:${user.home}/.local/share/piper/piper/piper}")
          String piper,
      @Value("${petitesroues.speech.voices:${user.home}/.local/share/piper/voices}")
          String voices) {
    this.library = library;
    this.piper = Path.of(piper);
    this.voices = Path.of(voices);
  }

  /**
   * Returns WAV audio of the text spoken in the title's source language.
   *
   * @throws IllegalArgumentException if the text is blank or longer than {@value MAX_TEXT_LENGTH}
   *     characters
   * @throws SpeechUnavailableException if Piper or a voice for the language is not installed
   */
  public byte[] speak(String titleId, String text) throws IOException {
    String spoken = text == null ? "" : text.strip();
    if (spoken.isEmpty()) throw new IllegalArgumentException("There is no text to read aloud");
    if (spoken.length() > MAX_TEXT_LENGTH)
      throw new IllegalArgumentException("Text is too long to read aloud");
    var language = Locale.forLanguageTag(library.document(titleId).sourceLanguage());
    return voice(language.getLanguage()).synthesize(speakable(spoken, language));
  }

  /**
   * Lettering is often entirely upper case, which voices may read as initials; such text is spoken
   * in lower case. Mixed-case text is left alone so proper nouns keep their capitals.
   */
  static String speakable(String text, Locale language) {
    return text.equals(text.toUpperCase(language)) ? text.toLowerCase(language) : text;
  }

  /** First model, by file name, whose name starts with the language followed by '_' or '-'. */
  static Path model(Path voices, String language) throws IOException {
    if (!Files.isDirectory(voices))
      throw new SpeechUnavailableException("No Piper voices directory at " + voices);
    try (var files = Files.list(voices)) {
      return files
          .filter(
              file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".onnx")
                    && (name.startsWith(language + "_") || name.startsWith(language + "-"));
              })
          .sorted()
          .findFirst()
          .orElseThrow(
              () ->
                  new SpeechUnavailableException(
                      "No Piper voice for '" + language + "' in " + voices));
    }
  }

  private PiperVoice voice(String language) throws IOException {
    var voice = running.get(language);
    if (voice != null) return voice;
    if (!Files.isExecutable(piper))
      throw new SpeechUnavailableException("Piper is not installed at " + piper);
    var model = model(voices, language);
    return running.computeIfAbsent(language, ignored -> new PiperVoice(piper, model));
  }

  @Override
  public void destroy() {
    running.values().forEach(PiperVoice::close);
    running.clear();
  }
}
