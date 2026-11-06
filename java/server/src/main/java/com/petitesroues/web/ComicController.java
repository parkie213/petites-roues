package com.petitesroues.web;

import com.petitesroues.comic.ComicService;
import com.petitesroues.comic.ReadingProgressService;
import com.petitesroues.library.LibraryUpdateService;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.editing.ComicBoxCreate;
import com.petitesroues.model.editing.ComicBoxUpdate;
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
   * Saves a text correction to one line, in whichever box holds it, without accepting geometry or
   * annotations from the text editor.
   */
  @PutMapping("/lines/{lineId}")
  public StudyDocument edit(
      @PathVariable String id, @PathVariable String lineId, @RequestBody ComicTextEdit edit)
      throws IOException {
    return updates.editComicText(id, lineId, edit);
  }

  /** Adds one drawn box with one line; ids and reading order are assigned server-side. */
  @PostMapping("/boxes")
  public StudyDocument create(@PathVariable String id, @RequestBody ComicBoxCreate create)
      throws IOException {
    return updates.createComicBox(id, create);
  }

  /** Replaces one box's regions and optional order; its lines stay as they are. */
  @PutMapping("/boxes/{boxId}/placement")
  public StudyDocument updatePlacement(
      @PathVariable String id, @PathVariable String boxId, @RequestBody ComicBoxUpdate update)
      throws IOException {
    return updates.placeComicBox(id, boxId, update);
  }

  /** Removes one box, with all its lines, from the document. */
  @DeleteMapping("/boxes/{boxId}")
  public StudyDocument delete(@PathVariable String id, @PathVariable String boxId)
      throws IOException {
    return updates.deleteComicBox(id, boxId);
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
