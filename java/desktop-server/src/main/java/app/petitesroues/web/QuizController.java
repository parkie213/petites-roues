package app.petitesroues.web;

import app.petitesroues.quiz.ChapterQuizBuilder;
import app.petitesroues.quiz.ChapterQuizService;
import app.petitesroues.quiz.VideoQuizService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only quiz preparation for a title, a video or a comic chapter. Clipboard, downloads and
 * sharing stay client-side; neither route writes content or progress, and both are {@code
 * no-store}.
 */
@RestController
@RequestMapping("/api/titles/{id}")
public class QuizController {
  private final ChapterQuizService chapterQuizzes;
  private final VideoQuizService videoQuizzes;

  public QuizController(ChapterQuizService chapterQuizzes, VideoQuizService videoQuizzes) {
    this.chapterQuizzes = chapterQuizzes;
    this.videoQuizzes = videoQuizzes;
  }

  /** Inputs supplied by the quiz menu and its bundled teaching guide. */
  public record Selection(String chapterPageId, String rules, ChapterQuizBuilder.Type type) {}

  /** Prepares text and a filename from the current served document without modifying progress. */
  @PostMapping("/comic/quiz")
  public ResponseEntity<ChapterQuizBuilder.Brief> prepareChapter(
      @PathVariable String id, @RequestBody Selection selection) throws IOException {
    if (selection == null) throw new IllegalArgumentException("Quiz selection is required");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            chapterQuizzes.build(
                id, selection.chapterPageId(), selection.rules(), selection.type()));
  }

  /**
   * Prepares a video quiz: the menu, the resolved scope and, when the scope is valid, the brief.
   * Every parameter but {@code position} is optional; without {@code scope} the default rule
   * applies. An unreadable time range is a 200 reply with {@code valid} false, not an error.
   *
   * @param scope {@code whole} or {@code range}
   * @param start range start, {@code m:ss} or {@code h:mm:ss}
   * @param end range end in the same form
   * @param type a quiz type id such as {@code tenses}
   * @param position the player's position in seconds
   * @param duration the player's duration in seconds, when known
   */
  @GetMapping("/quiz")
  public ResponseEntity<VideoQuizService.Prepared> prepareVideo(
      @PathVariable String id,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false) String type,
      @RequestParam double position,
      @RequestParam(required = false) Double duration)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(videoQuizzes.prepare(id, scope, start, end, type, position, duration));
  }
}
