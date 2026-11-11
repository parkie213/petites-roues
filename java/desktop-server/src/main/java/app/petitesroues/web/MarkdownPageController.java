package app.petitesroues.web;

import app.petitesroues.model.library.pages.MarkdownPageEntry;
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
  public ResponseEntity<List<PageResponse>> quizzes() throws IOException {
    return ok(pages.pages(MarkdownPageService.QUIZZES));
  }

  /** Every study note with its text, read from the repositories' files now. */
  @GetMapping("/notes")
  public ResponseEntity<List<PageResponse>> notes() throws IOException {
    return ok(pages.pages(MarkdownPageService.NOTES));
  }

  private static ResponseEntity<List<PageResponse>> ok(List<MarkdownPageEntry> body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(body.stream().map(PageResponse::from).toList());
  }

  /** Flat HTTP representation of a page and its library context. */
  public record PageResponse(
      String repository, String repositoryName, String id, String title, String text) {
    static PageResponse from(MarkdownPageEntry entry) {
      String filename = entry.path().substring(entry.path().lastIndexOf('/') + 1);
      return new PageResponse(
          entry.repository().id(),
          entry.repository().name(),
          filename.substring(0, filename.length() - ".md".length()),
          entry.content().title(),
          entry.content().text());
    }
  }
}
