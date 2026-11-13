package app.petitesroues.web;

import app.petitesroues.model.speech.VoiceLanguage;
import app.petitesroues.speech.SpeechService;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Lists installed voices and delegates persisted source-language preferences to speech logic. */
@RestController
@RequestMapping("/api/speech/voices")
public class SpeechVoicesController {
  private final SpeechService speech;

  public SpeechVoicesController(SpeechService speech) {
    this.speech = speech;
  }

  @GetMapping
  public List<VoiceLanguage> voices() throws IOException {
    return speech.voices();
  }

  /** A null voice restores the automatic choice. IDs are validated installed filenames. */
  public record Selection(String voice) {}

  @PutMapping("/{language}")
  public List<VoiceLanguage> select(@PathVariable String language, @RequestBody Selection selection)
      throws IOException {
    return speech.selectVoice(language, selection.voice());
  }
}
