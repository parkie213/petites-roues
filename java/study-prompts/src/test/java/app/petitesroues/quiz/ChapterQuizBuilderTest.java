package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChapterQuizBuilderTest {
  private static ComicPage page(String id, String label, Line... lines) {
    return new ComicPage(
        id, id + ".png", label, List.of(new ComicBox("box-" + id, List.of(), List.of(lines))));
  }

  private static StudyDocument document() {
    var pages =
        List.of(
            page("cover", null, Line.of("cover-text", "Outside before")),
            page(
                "a",
                null,
                Line.of("one", " Salut ")
                    .withTranslation(" Hi! ")
                    .withNotes(List.of("SECRET NOTE")),
                Line.of("blank", " ")),
            page(
                "b",
                "iii",
                Line.of("two", "Merci"),
                Line.of("three", "Au revoir").withTranslation("Goodbye")),
            page("c", null, Line.of("after", "Outside after")));
    return new StudyDocument(
        "comic",
        "A Comic — Volume 1",
        "fr",
        "en",
        "en",
        null,
        null,
        new ComicDocument(
            null,
            null,
            null,
            pages,
            List.of(new ComicChapter(1, "First", "a"), new ComicChapter(null, "Bonus", "c"))));
  }

  @Test
  void buildsOnlyChapterTextInReadingOrderWithRulesAndLanguageRoles() {
    var brief =
        ChapterQuizBuilder.build(
            "# Rules\n\nOne at a time.",
            document(),
            0,
            new ChapterQuizBuilder.Type("Vocabulary", "Ask about words."));
    var text = brief.text();
    assertTrue(text.startsWith("# Chapter quiz: A Comic — Volume 1, chapter 1 · First\n"));
    assertTrue(
        text.contains("Source language: `fr`; translation language: `en`; notes language: `en`"));
    assertTrue(text.contains("## Quiz type: Vocabulary\n\nAsk about words."));
    assertTrue(text.contains("### Rules\n\nOne at a time."));
    assertTrue(text.contains("## Bubbles in chapter 1 · First (pages 2–3)"));
    assertTrue(
        text.contains(
            "### Page 2\n\n- Salut → Hi!\n\n### Page iii\n\n- Merci\n- Au revoir → Goodbye\n"));
    assertFalse(text.contains("SECRET NOTE") || text.contains("Outside") || text.contains("-  \n"));
    assertEquals("quiz-a-comic-volume-1-chapter-1.md", brief.fileName());
    assertEquals(
        "quiz-a-comic-volume-1-bonus.md",
        ChapterQuizBuilder.build("", document(), 1, null).fileName());
  }

  @Test
  void emptyChapterHasExplanationRatherThanMixedOrderInstruction() {
    var document =
        document()
            .withComic(
                comic ->
                    comic
                        .withPage(
                            1,
                            page -> new ComicPage(page.id(), page.entry(), page.label(), List.of()))
                        .withPage(
                            2,
                            page ->
                                new ComicPage(page.id(), page.entry(), page.label(), List.of())));
    var text = ChapterQuizBuilder.build("", document, 0, null).text();
    assertTrue(text.contains("None: this chapter's text has not been found yet."));
    assertFalse(text.contains("Pick items"));
    assertThrows(
        IllegalArgumentException.class, () -> ChapterQuizBuilder.build("", document, 99, null));
    assertThrows(
        IllegalArgumentException.class, () -> ChapterQuizBuilder.build(null, document, 0, null));
  }
}
