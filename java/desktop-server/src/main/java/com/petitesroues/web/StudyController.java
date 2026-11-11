package com.petitesroues.web;

import com.petitesroues.library.LibraryService;
import com.petitesroues.library.LibraryUpdateService;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.editing.MergeCues;
import com.petitesroues.model.editing.SplitCue;
import com.petitesroues.model.editing.UpdateCueTiming;
import com.petitesroues.model.editing.VideoCueEdit;
import com.petitesroues.model.review.CorrectionSuggestion;
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
  private final LibraryUpdateService updates;

  public StudyController(LibraryService library, LibraryUpdateService updates) {
    this.library = library;
    this.updates = updates;
  }

  /**
   * GET the current document, including cue IDs, source text, translations, notes and listening
   * checks.
   */
  @GetMapping
  public ResponseEntity<StudyDocument> document(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.document(id));
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

  /**
   * POST timing changes for one or more existing cues; other cues and annotations are retained.
   *
   * <pre>{@code
   * [{"id":"cue-1","start":2.5,"end":4.1}]
   * }</pre>
   *
   * Returns {@code {"ok":true,"lines":50}}, where lines is the total cue count, not the edit count.
   */
  @PostMapping("/timings")
  public SavedTimings save(@PathVariable String id, @RequestBody List<UpdateCueTiming> edits)
      throws IOException {
    return new SavedTimings(true, updates.saveCueTiming(id, edits));
  }

  public record SavedTimings(boolean ok, int lines) {}

  /**
   * Queues {@code {"suggestion":"What the user heard"}} for the cue addressed by ID. The latest
   * document supplies the cue; no earlier cue state is submitted or compared.
   */
  @PostMapping("/cues/{cueId}/suggestion")
  public StudyDocument suggestCorrection(
      @PathVariable String id,
      @PathVariable String cueId,
      @RequestBody CorrectionSuggestion suggestion)
      throws IOException {
    return updates.suggestCorrection(id, cueId, suggestion.suggestion());
  }

  /**
   * Queues {@code {"text":"..."}} as a review comment on a video or comic cue, such as a better
   * translation or a question about a note. The document is unchanged; returns it as served.
   */
  @PostMapping("/cues/{cueId}/comment")
  public StudyDocument comment(
      @PathVariable String id, @PathVariable String cueId, @RequestBody Map<String, String> body)
      throws IOException {
    return updates.comment(id, cueId, body == null ? null : body.get("text"));
  }

  /**
   * PUT a complete {@link VideoCueEdit} command with explicit timing and source text. Teaching
   * fields are flat in this application request, whereas returned spec cues nest them under study.
   * The addressed identity and phrase alignment remain server-owned.
   *
   * <p>Source-text, translation or continuation changes mark the phrase alignment stale.
   * Source-text changes retain the existing listening check. An edit that changes nothing is not
   * queued.
   */
  @PutMapping("/cues/{cueId}")
  public StudyDocument editCue(
      @PathVariable String id, @PathVariable String cueId, @RequestBody VideoCueEdit cue)
      throws IOException {
    return updates.editCue(id, cueId, cue);
  }

  /**
   * POST a new cue. The server assigns its ID, inserts it in time order and queues annotation
   * review. The body may also supply translations, notes, continuation and a listening check.
   *
   * <pre>{@code
   * {"start":2.5,"end":4.1,"source":"Bonsoir !","notes":[]}
   * }</pre>
   */
  @PostMapping("/cues")
  public StudyDocument addCue(@PathVariable String id, @RequestBody VideoCueEdit cue)
      throws IOException {
    return updates.addCue(id, cue);
  }

  /**
   * POST two nonblank source-text parts and a split time strictly inside the cue's bounds.
   *
   * <pre>{@code
   * {"at":3.2,"first":"Bonsoir !","second":"Comment vas-tu ?"}
   * }</pre>
   *
   * Optional {@code start}/{@code end} override the outer bounds in the same operation. The first
   * part keeps the original ID and annotations; the second gets a new ID with no translation or
   * notes. Both retain the listening check and require annotation review. Reconsider both parts:
   * retained annotations may no longer fit the first part alone.
   */
  @PostMapping("/cues/{cueId}/split")
  public StudyDocument splitCue(
      @PathVariable String id, @PathVariable String cueId, @RequestBody SplitCue split)
      throws IOException {
    return updates.splitCue(id, cueId, split);
  }

  /**
   * POST {@code {"ids":["cue-1","cue-2"]}} to combine consecutive cues, in chronological order.
   * Selection order does not matter. Keeps the earliest ID/start and the latest end, joins source
   * text with spaces, and retains outstanding checks. The first cue's annotations remain
   * provisional; a merge task records all affected cue IDs, including those removed by the merge.
   * Returns the updated document; invalid or nonconsecutive selections return 400.
   */
  @PostMapping("/cues/merge")
  public StudyDocument mergeCues(@PathVariable String id, @RequestBody MergeCues merge)
      throws IOException {
    return updates.mergeCues(id, merge);
  }

  /**
   * DELETE a cue, with no body. Its ID remains in the queued deletion; returns the document with
   * the cue removed.
   */
  @DeleteMapping("/cues/{cueId}")
  public StudyDocument deleteCue(@PathVariable String id, @PathVariable String cueId)
      throws IOException {
    return updates.deleteCue(id, cueId);
  }
}
