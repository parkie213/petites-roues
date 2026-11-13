package app.petitesroues.web;

import app.petitesroues.comic.ComicService;
import app.petitesroues.comic.ReadingProgressService;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.ComicBoxCreate;
import app.petitesroues.model.editing.ComicBoxUpdate;
import app.petitesroues.model.editing.ComicTextEdit;
import app.petitesroues.model.progress.ReadingProgress;
import app.petitesroues.study.DocumentEditingService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Comic page images and local reading progress, independent of timed subtitle operations, and the
 * pages of a CBZ no study document references yet, by fingerprint.
 */
@RestController
@RequestMapping("/api")
public class ComicController {
  private final ComicService comics;
  private final DocumentEditingService editing;
  private final ReadingProgressService progress;

  public ComicController(
      ComicService comics, ReadingProgressService progress, DocumentEditingService editing) {
    this.comics = comics;
    this.editing = editing;
    this.progress = progress;
  }

  /** Opens a registered comic after checking every manifest entry against its local archive. */
  @GetMapping("/titles/{id}/comic")
  public ResponseEntity<StudyDocument> open(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comics.open(id));
  }

  @GetMapping("/titles/{id}/comic/pages/{pageId}")
  public ResponseEntity<byte[]> image(@PathVariable String id, @PathVariable String pageId)
      throws IOException {
    return image(comics.image(id, pageId), CacheControl.noStore());
  }

  /** A CBZ's pages by entry name, in reading order, for reading it without a document. */
  @GetMapping("/media/{fingerprint}/pages")
  public ResponseEntity<List<String>> pages(@PathVariable String fingerprint) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comics.pages(fingerprint));
  }

  /**
   * Page {@code index} of {@link #pages}. The fingerprint names the file's content, so the browser
   * may keep a page for a day and turning back does not fetch it again.
   */
  @GetMapping("/media/{fingerprint}/pages/{index}")
  public ResponseEntity<byte[]> page(@PathVariable String fingerprint, @PathVariable int index)
      throws IOException {
    return image(comics.page(fingerprint, index), CacheControl.maxAge(Duration.ofDays(1)));
  }

  private static ResponseEntity<byte[]> image(ComicService.PageImage image, CacheControl cache) {
    return ResponseEntity.ok()
        .cacheControl(cache)
        .contentType(
            MediaTypeFactory.getMediaType(image.name()).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(image.bytes());
  }

  /**
   * Saves a text correction to one line, in whichever box holds it, without accepting geometry or
   * annotations from the text editor.
   */
  @PutMapping("/titles/{id}/comic/lines/{lineId}")
  public StudyDocument edit(
      @PathVariable String id, @PathVariable String lineId, @RequestBody ComicTextEdit edit)
      throws IOException {
    return editing.editComicText(id, lineId, edit);
  }

  /** Adds one drawn box with one line; ids and reading order are assigned server-side. */
  @PostMapping("/titles/{id}/comic/boxes")
  public StudyDocument create(@PathVariable String id, @RequestBody ComicBoxCreate create)
      throws IOException {
    return editing.createComicBox(id, create);
  }

  /** Replaces one box's regions and optional order; its lines stay as they are. */
  @PutMapping("/titles/{id}/comic/boxes/{boxId}/placement")
  public StudyDocument updatePlacement(
      @PathVariable String id, @PathVariable String boxId, @RequestBody ComicBoxUpdate update)
      throws IOException {
    return editing.placeComicBox(id, boxId, update);
  }

  /** Removes one box, with all its lines, from the document. */
  @DeleteMapping("/titles/{id}/comic/boxes/{boxId}")
  public StudyDocument delete(@PathVariable String id, @PathVariable String boxId)
      throws IOException {
    return editing.deleteComicBox(id, boxId);
  }

  @GetMapping("/titles/{id}/comic/progress")
  public ResponseEntity<ReadingProgress> load(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(progress.load(id));
  }

  /** Forgets the reading position; the library card then shows no progress. */
  @DeleteMapping("/titles/{id}/comic/progress")
  public ResponseEntity<Void> clear(@PathVariable String id) throws IOException {
    progress.clear(id);
    return ResponseEntity.noContent().build();
  }

  public record Position(String pageId) {}

  @PutMapping("/titles/{id}/comic/progress")
  public ReadingProgress save(@PathVariable String id, @RequestBody Position position)
      throws IOException {
    return progress.save(id, position == null ? null : position.pageId());
  }
}
