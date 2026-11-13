package app.petitesroues.web;

import app.petitesroues.model.speech.VoiceCatalogue;
import app.petitesroues.model.speech.VoiceLanguage;
import app.petitesroues.speech.SpeechService;
import app.petitesroues.speech.VoiceCatalogueService;
import app.petitesroues.web.ApiExceptionHandler.ApiError;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Lists installed voices and delegates persisted source-language preferences to speech logic. */
@RestController
@RequestMapping("/api/speech/voices")
public class SpeechVoicesController {
  private final SpeechService speech;
  private final VoiceCatalogueService catalogue;

  public SpeechVoicesController(SpeechService speech, VoiceCatalogueService catalogue) {
    this.speech = speech;
    this.catalogue = catalogue;
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

  @GetMapping("/catalogue")
  public VoiceCatalogue catalogue(@RequestParam(defaultValue = "false") boolean refresh)
      throws IOException, InterruptedException {
    return catalogue.catalogue(refresh);
  }

  /** Installation does not change the saved voice preference. */
  @PostMapping("/catalogue/{id}/install")
  public List<VoiceLanguage> install(@PathVariable String id)
      throws IOException, InterruptedException {
    catalogue.install(id);
    return speech.voices();
  }

  @GetMapping("/catalogue/{id}/sample")
  public ResponseEntity<byte[]> sample(@PathVariable String id)
      throws IOException, InterruptedException {
    return ResponseEntity.ok().header("Content-Type", "audio/mpeg").body(catalogue.sample(id));
  }

  @GetMapping("/{language}/preview")
  public ResponseEntity<byte[]> preview(
      @PathVariable String language,
      @RequestParam(required = false) String voice,
      @RequestParam String text)
      throws IOException {
    return ResponseEntity.ok()
        .header("Content-Type", "audio/wav")
        .cacheControl(CacheControl.noStore())
        .body(speech.preview(language, voice, text));
  }

  /** Provider and preview failures remain actionable in the dialog instead of a generic 500. */
  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiError unavailable(IOException failure) {
    return new ApiError("Voice operation failed: " + failure.getMessage());
  }
}
