package app.petitesroues.speech;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.library.LibraryService;
import app.petitesroues.settings.SettingsStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpeechServiceTest {
  @Test
  void routesSpeechThroughThePersistedPreferenceAndRejectsWrongLanguageSelection()
      throws Exception {
    var library = mock(LibraryService.class);
    var engine = mock(PiperSynthesizer.class);
    var settings = mock(SettingsStore.class);
    when(library.document("title")).thenReturn(document("title"));
    when(settings.get("speech.voice.fr")).thenReturn(Optional.of("fr_FR-b.onnx"));
    when(engine.installedVoices())
        .thenReturn(Map.of("fr", List.of("fr_FR-a.onnx", "fr_FR-b.onnx")));
    var service = new SpeechService(library, engine, settings);
    service.speak("title", "BONJOUR");
    verify(engine).synthesize("fr", "fr_FR-b.onnx", "bonjour");
    assertThrows(IllegalArgumentException.class, () -> service.selectVoice("fr", "en_GB-a.onnx"));
    verify(settings, never()).put(anyString(), anyString());
    service.selectVoice("fr", null);
    verify(settings).put("speech.voice.fr", "");
  }

  @Test
  void previewUsesTheSameLanguageAndTextRulesWithoutReadingADocumentOrSavingAChoice()
      throws Exception {
    var library = mock(LibraryService.class);
    var engine = mock(PiperSynthesizer.class);
    var settings = mock(SettingsStore.class);
    var service = new SpeechService(library, engine, settings);
    service.preview("fr-FR", "fr_FR-test.onnx", " BONJOUR ");
    verify(engine).synthesize("fr", "fr_FR-test.onnx", "bonjour");
    verifyNoInteractions(library, settings);
    assertThrows(IllegalArgumentException.class, () -> service.preview("fr", null, " "));
  }

  @Test
  void speaksAllCapsLetteringInLowerCaseButKeepsMixedCase() {
    var french = Locale.forLanguageTag("fr");
    assertEquals(
        "tu es descendu par ce temps ?",
        SpeechService.speakable("TU ES DESCENDU PAR CE TEMPS ?", french));
    assertEquals("ÇA VA, Tanjiro ?", SpeechService.speakable("ÇA VA, Tanjiro ?", french));
    assertEquals("ça va", SpeechService.speakable("ÇA VA", french));
  }
}
