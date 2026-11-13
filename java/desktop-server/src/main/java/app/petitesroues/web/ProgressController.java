package app.petitesroues.web;

import app.petitesroues.model.progress.ProgressPosition;
import app.petitesroues.model.progress.SavedProgress;
import app.petitesroues.progress.LearnerProgressService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Local playback progress, independent of subtitle edits, annotations and exported documents. */
@RestController
@RequestMapping("/api/titles/{id}/progress")
public class ProgressController {
  private final LearnerProgressService progress;

  public ProgressController(LearnerProgressService progress) {
    this.progress = progress;
  }

  @GetMapping
  public ResponseEntity<SavedProgress> load(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(progress.load(id));
  }

  /** Forgets the resume position; the library card then shows no progress. */
  @DeleteMapping
  public ResponseEntity<Void> clear(@PathVariable String id) throws IOException {
    progress.clear(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping
  public SavedProgress save(@PathVariable String id, @RequestBody JsonNode position)
      throws IOException {
    if (position == null || !position.isObject() || position.size() != 1)
      throw new IllegalArgumentException("Supply exactly one positionSeconds or pageId");
    if (position.has("positionSeconds") && position.get("positionSeconds").isNumber())
      return progress.save(
          id, new ProgressPosition.Video(position.get("positionSeconds").asDouble()));
    if (position.has("pageId") && position.get("pageId").isString())
      return progress.save(id, new ProgressPosition.Comic(position.get("pageId").asString()));
    throw new IllegalArgumentException("Supply numeric positionSeconds or a pageId string");
  }
}
