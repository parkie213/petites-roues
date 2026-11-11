package app.petitesroues.web;

import app.petitesroues.library.LibraryUpdateService;
import app.petitesroues.model.review.CueChange;
import app.petitesroues.model.review.CueReviewRequest;
import app.petitesroues.model.review.ReviewCompletion;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ReviewQueueService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The review queue: player edits waiting to be written into document files, and the batch API
 * agents use to write them.
 *
 * <p>Agent loop for a title: POST {@code /api/titles/{id}/review/batches} to claim the oldest
 * pending edits. The response holds the claimed {@code changes} (each with its {@code command}) and
 * {@code document}, the file plus exactly those edits. Write that document into the registered
 * {@code document.json}, updating translations, notes, alignments and checks for the affected cues,
 * commit and push, then POST {@code .../batches/{batch}/complete}. Claiming again before completing
 * returns the same open batch. Edits made after the claim stay queued and are replayed over the
 * agent's file.
 */
@RestController
@RequestMapping("/api")
public class ReviewController {
  private final ReviewQueueService review;
  private final LibraryUpdateService updates;

  public ReviewController(ReviewQueueService review, LibraryUpdateService updates) {
    this.review = review;
    this.updates = updates;
  }

  /** GET titles with pending queue rows, oldest work first, and any open batch. */
  @GetMapping("/review/pending")
  public ResponseEntity<List<ReviewQueue.PendingTitle>> pendingTitles() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(review.pendingTitles());
  }

  /**
   * GET pending queue rows for a title as {@link CueChange}s, in sequence order, each with its
   * replay {@code state}: pending, conflict, written or review.
   */
  @GetMapping({"/titles/{id}/review-queue", "/titles/{id}/changes"})
  public ResponseEntity<List<CueChange>> pending(@PathVariable String id) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(review.pending(id));
  }

  /** Completed rows and their decisions. */
  @GetMapping("/titles/{id}/review-history")
  public ResponseEntity<List<CueChange>> history(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(review.history(id));
  }

  /**
   * POST {@code [{"cueId":"cue-0001","text":"First-pass notes from openai/gpt-6-luna"}]} to queue
   * cues for the reviewing agent, for example after a preparation script. All or none are queued;
   * returns {@code {"queued":n}}. The document is unchanged, and players don't count these as their
   * own pending edits.
   */
  @PostMapping("/titles/{id}/review/requests")
  public Map<String, Integer> requestReview(
      @PathVariable String id, @RequestBody List<CueReviewRequest> requests) throws IOException {
    synchronized (updates) {
      return Map.of("queued", review.requestReview(id, requests));
    }
  }

  /**
   * POST to claim up to {@code limit} (default 50) of the oldest pending edits, or to get the open
   * batch. Returns 204 when nothing is pending.
   */
  @PostMapping("/titles/{id}/review/batches")
  public ResponseEntity<ReviewQueueService.BatchView> claim(
      @PathVariable String id, @RequestParam(defaultValue = "50") int limit) throws IOException {
    synchronized (updates) {
      return review
          .claim(id, limit)
          .map(batch -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(batch))
          .orElseGet(() -> ResponseEntity.noContent().build());
    }
  }

  /**
   * POST {@code {"outcome":"applied","outcomes":{"12":"not_adopted"}}} after the batch's edits are
   * written and checkpointed. Outcomes are applied (default), already_correct, not_adopted and
   * reviewed. Rejected while document.json is unchanged since the claim, unless every edit is
   * not_adopted.
   */
  @PostMapping("/titles/{id}/review/batches/{batch}/complete")
  public void complete(
      @PathVariable String id,
      @PathVariable long batch,
      @RequestBody(required = false) ReviewCompletion completion)
      throws IOException {
    synchronized (updates) {
      review.complete(id, batch, completion);
    }
  }

  /** POST the displayed pending sequences to discard those edits, reverting them in the player. */
  @PostMapping("/titles/{id}/review-queue/discard")
  public void discard(@PathVariable String id, @RequestBody List<Long> expected) {
    synchronized (updates) {
      review.discard(id, expected);
    }
  }
}
