package com.petitesroues.web;

import com.petitesroues.comic.ComicService;
import com.petitesroues.comic.ReadingProgressService;
import com.petitesroues.library.LibraryUpdateService;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.editing.ComicCueCreate;
import com.petitesroues.model.editing.ComicCueUpdate;
import com.petitesroues.model.editing.ComicTextEdit;
import com.petitesroues.model.progress.ReadingProgress;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Comic page images and local reading progress, independent of timed subtitle operations. */
@RestController
@RequestMapping("/api/titles/{id}/comic")
public class ComicController {
  private final ComicService comics;
  private final LibraryUpdateService updates;
  private final ReadingProgressService progress;

  public ComicController(
      ComicService comics, ReadingProgressService progress, LibraryUpdateService updates) {
    this.comics = comics;
    this.updates = updates;
    this.progress = progress;
  }

  /** Opens a registered comic after checking every manifest entry against its local archive. */
  @GetMapping
  public ResponseEntity<StudyDocument> open(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comics.open(id));
  }

  @GetMapping("/pages/{pageId}")
  public ResponseEntity<byte[]> image(@PathVariable String id, @PathVariable String pageId)
      throws IOException {
    var image = comics.image(id, pageId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaTypeFactory.getMediaType(image.name()).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(image.bytes());
  }

  /**
   * Saves a text correction to one study, in whichever box holds it, without accepting geometry or
   * annotations from the text editor.
   */
  @PutMapping("/studies/{studyId}")
  public StudyDocument edit(
      @PathVariable String id, @PathVariable String studyId, @RequestBody ComicTextEdit edit)
      throws IOException {
    return updates.editComicText(id, studyId, edit);
  }

  /** Adds one drawn box with one study; ids and reading order are assigned server-side. */
  @PostMapping("/cues")
  public StudyDocument create(@PathVariable String id, @RequestBody ComicCueCreate create)
      throws IOException {
    return updates.createComicCue(id, create);
  }

  /** Replaces one box's regions and optional order; its studies stay as they are. */
  @PutMapping("/cues/{cueId}/placement")
  public StudyDocument updatePlacement(
      @PathVariable String id, @PathVariable String cueId, @RequestBody ComicCueUpdate update)
      throws IOException {
    return updates.placeComicCue(id, cueId, update);
  }

  /** Removes one box, with all its studies, from the document. */
  @DeleteMapping("/cues/{cueId}")
  public StudyDocument delete(@PathVariable String id, @PathVariable String cueId)
      throws IOException {
    return updates.deleteComicCue(id, cueId);
  }

  @GetMapping("/progress")
  public ResponseEntity<ReadingProgress> load(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(progress.load(id));
  }

  /** Forgets the reading position; the library card then shows no progress. */
  @DeleteMapping("/progress")
  public ResponseEntity<Void> clear(@PathVariable String id) throws IOException {
    progress.clear(id);
    return ResponseEntity.noContent().build();
  }

  public record Position(String pageId) {}

  @PutMapping("/progress")
  public ReadingProgress save(@PathVariable String id, @RequestBody Position position)
      throws IOException {
    return progress.save(id, position == null ? null : position.pageId());
  }
}
