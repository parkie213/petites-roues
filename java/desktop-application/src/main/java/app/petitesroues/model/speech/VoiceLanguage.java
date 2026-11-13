package app.petitesroues.model.speech;

import java.util.List;

/** Installed voice filenames for one source language, with a nullable explicit preference. */
public record VoiceLanguage(String language, String selectedVoice, List<String> voices) {
  public VoiceLanguage {
    voices = List.copyOf(voices);
  }
}
