package app.petitesroues.comic;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.*;
import app.petitesroues.quiz.ChapterQuizBuilder;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChapterQuizIntegrationTest extends HttpIntegrationTest {
  @Test
  void preparesFromQueuedEditsWithoutWritingDocumentsOrProgress() throws Exception {
    var line = Line.of("bubble", "Salut").withTranslation("Hi");
    var page =
        new ComicPage(
            "page",
            "001.jpg",
            null,
            List.of(new ComicBox("box", List.of(new ComicRegion(.1, .1, .2, .2)), List.of(line))));
    var document =
        new StudyDocument(
            "comic",
            "Comic",
            "fr",
            "en",
            "en",
            null,
            null,
            new ComicDocument(
                null, null, null, List.of(page), List.of(new ComicChapter(1, "First", "page"))));
    registerTitle(document);
    var original = Files.readString(documentPath("comic"));
    http.put()
        .uri("/api/titles/comic/comic/lines/bubble")
        .body(Map.of("expectedSource", "Salut", "source", "Bonjour"))
        .retrieve()
        .toBodilessEntity();
    var result =
        http.post()
            .uri("/api/titles/comic/comic/quiz")
            .body(Map.of("chapterPageId", "page", "rules", "# Rules\nOne at a time."))
            .retrieve()
            .toEntity(ChapterQuizBuilder.Brief.class);
    assertTrue(result.getBody().text().contains("- Bonjour\n"));
    assertFalse(
        result.getBody().text().contains("- Salut") || result.getBody().text().contains(" → Hi"));
    assertEquals("quiz-comic-chapter-1.md", result.getBody().fileName());
    assertTrue(result.getHeaders().getCacheControl().contains("no-store"));
    assertEquals(original, Files.readString(documentPath("comic")));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reading_progress", Integer.class));
    assertEquals(
        404,
        http.post()
            .uri("/api/titles/comic/comic/quiz")
            .body(Map.of("chapterPageId", "missing", "rules", ""))
            .<Integer>exchange((request, response) -> response.getStatusCode().value()));
    assertEquals(
        400,
        http.post()
            .uri("/api/titles/comic/comic/quiz")
            .body(Map.of("chapterPageId", "page"))
            .<Integer>exchange((request, response) -> response.getStatusCode().value()));
  }
}
