package app.petitesroues.web;

import app.petitesroues.model.progress.PlaybackProgress;
import app.petitesroues.playback.PlaybackProgressService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Local playback progress, independent of subtitle edits, annotations and exported documents. */
@RestController
@RequestMapping("/api/titles/{id}/progress")
public class PlaybackProgressController {
  private final PlaybackProgressService progress;

  public PlaybackProgressController(PlaybackProgressService progress) {
    this.progress = progress;
  }

  public record Position(Double positionSeconds) {}

  @GetMapping
  public ResponseEntity<PlaybackProgress> load(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(progress.load(id));
  }

  /** Forgets the resume position; the library card then shows no progress. */
  @DeleteMapping
  public ResponseEntity<Void> clear(@PathVariable String id) {
    progress.clear(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping
  public PlaybackProgress save(@PathVariable String id, @RequestBody Position position) {
    return progress.save(id, position == null ? null : position.positionSeconds());
  }
}
