package app.petitesroues.web;

import app.petitesroues.model.pages.MarkdownPage;
import app.petitesroues.pages.MarkdownPageService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The library's Markdown pages: general quizzes for "Quiz me" and study notes for "Notes". */
@RestController
@RequestMapping("/api")
public class MarkdownPageController {
  private final MarkdownPageService pages;

  public MarkdownPageController(MarkdownPageService pages) {
    this.pages = pages;
  }

  /** Every general quiz with its text, read from the repositories' files now. */
  @GetMapping("/quizzes")
  public ResponseEntity<List<MarkdownPage>> quizzes() throws IOException {
    return ok(pages.pages(MarkdownPageService.QUIZZES));
  }

  /** Every study note with its text, read from the repositories' files now. */
  @GetMapping("/notes")
  public ResponseEntity<List<MarkdownPage>> notes() throws IOException {
    return ok(pages.pages(MarkdownPageService.NOTES));
  }

  private static ResponseEntity<List<MarkdownPage>> ok(List<MarkdownPage> body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
