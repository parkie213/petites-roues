package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SpeechServiceTest {
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
