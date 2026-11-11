package com.petitesroues.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.petitesroues.model.library.IndexedMedia;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioLanguagesTest {
  @Test
  void mediaLanguageTagsBecomeTheTagsDocumentsUse() {
    assertEquals(
        List.of("en", "fr", "fr", "nl", "de", "ja", "", "", "fr", "tlh"),
        List.of("eng", "fre", "fra", "dut", "ger", "jpn", "und", "", "FR", "tlh").stream()
            .map(AudioLanguages::tag)
            .toList());
    assertEquals("French", AudioLanguages.name("fr"));
  }

  private static IndexedMedia video(String languages) {
    return new IndexedMedia(
        "repo",
        "/media",
        "films/shrek/shrek.mkv",
        "video",
        1,
        1,
        "v1:shrek",
        60.0,
        "aac",
        true,
        languages,
        null,
        null,
        null);
  }

  @Test
  void warnsWhenTheFirstTrackIsNotTheDocumentsLanguage() {
    var shrek = video("en,fr,nl,en");
    assertEquals(
        "Plays English audio, not French: make the French track the first",
        shrek.languageWarning("fr"));
    assertEquals(
        "Plays English audio, not German: it has no German track", shrek.languageWarning("de"));
    assertEquals(null, shrek.languageWarning("en-GB"));
    assertEquals(null, video(",fr").languageWarning("fr"), "an untagged first track is unknown");
    assertEquals(null, video("fr,en").languageWarning("fr"));
  }

  @Test
  void namesWhichLanguagePlaysWhenThereAreSeveral() {
    assertEquals(
        "Plays English audio of English, French and Dutch", video("en,fr,nl,en").languagesNote());
    assertEquals(null, video("fr").languagesNote());
    assertEquals(null, video("fr,,fr").languagesNote());
  }
}
