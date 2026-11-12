package app.petitesroues.speech;

import app.petitesroues.library.LibraryService;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Reads a title's source-language text aloud with a local Piper voice. The voice is chosen from the
 * document's {@code sourceLanguage}: the first model in the voices directory whose file name starts
 * with that language (for example {@code fr_FR-siwis-medium.onnx} for {@code fr}). Native voice
 * ownership belongs to {@link PiperSynthesizer}. Audio is generated for every request and never
 * stored.
 */
@Service
public class SpeechService {
  static final int MAX_TEXT_LENGTH = 1000;

  private final LibraryService library;
  private final PiperSynthesizer synthesizer;

  public SpeechService(LibraryService library, PiperSynthesizer synthesizer) {
    this.library = library;
    this.synthesizer = synthesizer;
  }

  /**
   * Returns WAV audio of the text spoken in the title's source language.
   *
   * @throws IllegalArgumentException if the text is blank or longer than {@value MAX_TEXT_LENGTH}
   *     characters
   * @throws SpeechUnavailableException if the native library or a voice for the language is
   *     unavailable
   */
  public byte[] speak(String titleId, String text) throws IOException {
    String spoken = text == null ? "" : text.strip();
    if (spoken.isEmpty()) throw new IllegalArgumentException("There is no text to read aloud");
    if (spoken.length() > MAX_TEXT_LENGTH)
      throw new IllegalArgumentException("Text is too long to read aloud");
    var language = Locale.forLanguageTag(library.document(titleId).sourceLanguage());
    return synthesizer.synthesize(language.getLanguage(), speakable(spoken, language));
  }

  /**
   * Lettering is often entirely upper case, which voices may read as initials; such text is spoken
   * in lower case. Mixed-case text is left alone so proper nouns keep their capitals.
   */
  static String speakable(String text, Locale language) {
    return text.equals(text.toUpperCase(language)) ? text.toLowerCase(language) : text;
  }
}
