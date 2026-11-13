package app.petitesroues.web;

import app.petitesroues.library.LibraryService;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.CommandRequest;
import app.petitesroues.study.DocumentCommandService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * JSON API for the subtitles and annotations of an already imported title.
 *
 * <p>All routes below are relative to {@code /api/titles/{id}}, where {@code id} is the title ID
 * returned by import or the library catalogue. Send request bodies as {@code application/json}.
 * Times are seconds from the start of the video; start must be nonnegative and end must be greater
 * than start. Address cues by their stable IDs, never their list positions.
 *
 * <p>Edits are queued, never written to the document file: GET returns the file with pending edits
 * replayed, and agents write them into the file through review batches ({@link ReviewController}).
 * A subtitle correction does not confirm the spoken source text or resolve a listening check.
 *
 * <p>Successful calls return 200. VideoCue mutations return the whole updated {@link
 * StudyDocument}, allowing the player to replace its cue list after additions, splits or sorting.
 * Invalid requests return 400; missing titles/cues return 404; unexpected storage failures return
 * 500. Errors use {@code {"error":"message"}}.
 */
@RestController
@RequestMapping("/api/titles/{id}")
public class StudyController {
  private final LibraryService library;
  private final DocumentCommandService commands;

  public StudyController(LibraryService library, DocumentCommandService commands) {
    this.library = library;
    this.commands = commands;
  }

  /**
   * GET the current document, including cue IDs, source text, translations, notes and listening
   * checks.
   */
  @GetMapping
  public ResponseEntity<StudyDocument> document(@PathVariable String id) throws IOException {
    var state = commands.read(id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-State-Version", state.version())
        .body(state.document());
  }

  /**
   * POST a candidate {@code document.json} to check it before writing the file. Returns {@code
   * {"problems":["cue-0012: Translation phrase 2 belongs to more than one link", ...]}}; an empty
   * list means the file would load. Nothing is written or queued, and teaching quality is not
   * judged.
   */
  @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, List<String>> validate(@PathVariable String id, @RequestBody byte[] candidate)
      throws IOException {
    return Map.of("problems", library.problems(id, candidate));
  }

  /**
   * GET the current document as a downloadable {@code study.json}. Includes current annotations and
   * reference metadata, but excludes local file paths and the local review queue.
   */
  @GetMapping("/export")
  public ResponseEntity<StudyDocument> export(@PathVariable String id) throws IOException {
    StudyDocument document = library.document(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("study.json").build().toString())
        .body(document);
  }

  /** One atomic typed command batch; headers carry the state token and assigned identities. */
  @PostMapping("/commands")
  public ResponseEntity<StudyDocument> commands(
      @PathVariable String id, @RequestBody CommandRequest request) throws IOException {
    var result = commands.execute(id, request);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-State-Version", result.version())
        .header("X-Created-Ids", String.join(",", result.createdIds()))
        .header("X-Accepted-Version", result.acceptedVersion())
        .body(result.document());
  }
}
