package app.petitesroues.speech;

import app.petitesroues.library.LibraryService;
import app.petitesroues.model.speech.VoiceLanguage;
import app.petitesroues.settings.SettingsStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Reads a title's source-language text aloud with a local Piper voice. The voice is chosen from the
 * document's {@code sourceLanguage}, using its saved voice preference or the first matching model
 * by filename when no preference was set. Native voice ownership belongs to {@link
 * PiperSynthesizer}. Audio is generated for every request and never stored.
 */
@Service
public class SpeechService {
  static final int MAX_TEXT_LENGTH = 1000;

  private final LibraryService library;
  private final PiperSynthesizer synthesizer;
  private final SettingsStore settings;

  public SpeechService(
      LibraryService library, PiperSynthesizer synthesizer, SettingsStore settings) {
    this.library = library;
    this.synthesizer = synthesizer;
    this.settings = settings;
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
    return synthesizer.synthesize(
        language.getLanguage(), selected(language.getLanguage()), speakable(spoken, language));
  }

  /** Lists installed languages and preferences without opening models or loading native code. */
  public List<VoiceLanguage> voices() throws IOException {
    var result = new ArrayList<VoiceLanguage>();
    for (var entry : synthesizer.installedVoices().entrySet())
      result.add(new VoiceLanguage(entry.getKey(), selected(entry.getKey()), entry.getValue()));
    return List.copyOf(result);
  }

  /** Saves a matching installed voice in SQLite; null restores automatic filename ordering. */
  public List<VoiceLanguage> selectVoice(String language, String voice) throws IOException {
    var installed = synthesizer.installedVoices();
    if (!installed.containsKey(language))
      throw new IllegalArgumentException("No installed voices for " + language);
    if (voice != null && !installed.get(language).contains(voice))
      throw new IllegalArgumentException("Voice is not installed for " + language);
    settings.put("speech.voice." + language, voice == null ? "" : voice);
    return voices();
  }

  private String selected(String language) {
    return settings.get("speech.voice." + language).filter(value -> !value.isEmpty()).orElse(null);
  }

  /**
   * Lettering is often entirely upper case, which voices may read as initials; such text is spoken
   * in lower case. Mixed-case text is left alone so proper nouns keep their capitals.
   */
  static String speakable(String text, Locale language) {
    return text.equals(text.toUpperCase(language)) ? text.toLowerCase(language) : text;
  }
}
