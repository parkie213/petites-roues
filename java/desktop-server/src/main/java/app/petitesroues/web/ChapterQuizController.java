package app.petitesroues.web;

import app.petitesroues.quiz.ChapterQuizBuilder;
import app.petitesroues.quiz.ChapterQuizService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only chapter quiz preparation; browser clipboard, downloads and sharing stay client-side.
 */
@RestController
@RequestMapping("/api/titles/{id}/comic/quiz")
public class ChapterQuizController {
  private final ChapterQuizService quizzes;

  public ChapterQuizController(ChapterQuizService quizzes) {
    this.quizzes = quizzes;
  }

  /** Inputs supplied by the quiz menu and its bundled teaching guide. */
  public record Selection(String chapterPageId, String rules, ChapterQuizBuilder.Type type) {}

  /** Prepares text and a filename from the current served document without modifying progress. */
  @PostMapping
  public ResponseEntity<ChapterQuizBuilder.Brief> prepare(
      @PathVariable String id, @RequestBody Selection selection) throws IOException {
    if (selection == null) throw new IllegalArgumentException("Quiz selection is required");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(quizzes.build(id, selection.chapterPageId(), selection.rules(), selection.type()));
  }
}
