package com.petitesroues.comic;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentValidator;
import com.petitesroues.model.document.ComicBox;
import com.petitesroues.model.document.ComicChapter;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComicBoxValidationTest {
  /** A comic whose one page holds {@code boxes}. */
  private static StudyDocument comic(ComicBox... boxes) {
    return comic(List.of(newPage("page", "001.jpg", null, boxes)));
  }

  private static StudyDocument comic(List<ComicPage> pages) {
    return new StudyDocument(
        "comic",
        "Comic",
        "fr",
        "en",
        "en",
        null,
        null,
        null,
        null,
        new ComicDocument(null, ReadingDirection.RTL, null, pages, List.of()));
  }

  private static void valid(StudyDocument document) {
    assertDoesNotThrow(() -> StudyDocumentValidator.validate(document));
  }

  private static void invalid(StudyDocument document) {
    assertThrows(IllegalArgumentException.class, () -> StudyDocumentValidator.validate(document));
  }

  @Test
  void validatesNormalizedGeometry() {
    var box = newBubble(Line.of("bubble", "Salut !"), List.of(newRegion(.1, .2, .3, .4)));
    valid(comic(box));
    invalid(comic(box.withRegions(List.of())));
    for (var region :
        List.of(
            newRegion(-.1, 0, .1, .1),
            newRegion(.9, 0, .2, .1),
            newRegion(0, 0, 0, .1),
            newRegion(0, 0, Double.NaN, .1),
            newRegion(0, 0, .1, Double.POSITIVE_INFINITY)))
      invalid(comic(box.withRegions(List.of(region))));
    // Two boxes may overlap; their order is their position on the page.
    var other = newBubble(Line.of("other", "Oui"), box.regions());
    valid(comic(box, other));
    valid(comic(box, other.withLine("other", s -> s.withSource(""))));
    invalid(comic(box, other.withLine("other", s -> s.withId("bubble"))));
  }

  @Test
  void aBoxHasItsOwnUniqueIdAndOneOrMoreUniquelyIdentifiedLines() {
    var region = List.of(newRegion(.1, .6, .8, .3));
    var first = Line.of("s1", "Une phrase.");
    var second = Line.of("s2", "Une autre.");
    var block = new ComicBox("intro", region, List.of(first, second));
    var label = newBubble(Line.of("label", "Introduction"), region);
    valid(comic(block, label));

    invalid(comic(block.withLines(List.of())));
    invalid(comic(block.withLines(List.of(first, first))));
    invalid(comic(block.withLines(List.of(first, second.withId(" ")))));
    invalid(comic(block, newBubble(Line.of("s2", "Encore"), region)));
    invalid(comic(new ComicBox(" ", region, List.of(first))));
    invalid(comic(block, new ComicBox("intro", region, List.of(Line.of("x", "X")))));
    // Box and line IDs are separate namespaces.
    valid(comic(block, new ComicBox("s1", region, List.of(Line.of("x", "X")))));
    // Box and line IDs are unique across the whole comic, not per page.
    var elsewhere = new ComicBox("intro", region, List.of(Line.of("y", "Y")));
    invalid(
        comic(
            List.of(
                newPage("page", "001.jpg", null, block),
                newPage("next", "002.jpg", null, elsewhere))));
    invalid(
        comic(
            List.of(
                newPage("page", "001.jpg", null, block),
                newPage("next", "002.jpg", null, newBubble(first, region)))));
  }

  @Test
  void aLineIsReplacedInPlaceByItsOwnId() {
    var block =
        new ComicBox(
            "intro",
            List.of(newRegion(.1, .6, .8, .3)),
            List.of(Line.of("s1", "Une phrase."), Line.of("s2", "Une autre.")));
    var changed = block.withLine("s2", s -> s.withTranslation("Another."));
    assertEquals("Another.", changed.lines().get(1).translation());
    assertEquals(block.lines().get(0), changed.lines().get(0));
    assertEquals("intro", changed.id());
    assertTrue(changed.holds("s2"));
    assertThrows(IllegalArgumentException.class, () -> block.withLine("nope", s -> s));
  }

  @Test
  void volumeIsOptionalButPositive() {
    for (Integer volume : new Integer[] {null, 1, 12}) valid(comic().withComic(volume(volume)));
    for (int volume : new int[] {0, -1}) invalid(comic().withComic(volume(volume)));
  }

  private static ComicDocument volume(Integer volume) {
    var base = comic().comic();
    return new ComicDocument(
        volume, base.readingDirection(), base.annotationMethod(), base.pages(), base.chapters());
  }

  @Test
  void chaptersStartOnKnownPagesInReadingOrder() {
    valid(comic().withComic(chapters()));
    var one = new ComicChapter(1, "Cruauté", "b");
    var two = new ComicChapter(2, "Inconnu", "c");
    valid(comic().withComic(chapters(one, two)));
    valid(comic().withComic(chapters(new ComicChapter(null, "Bonus", "a"), one)));
    invalid(comic().withComic(chapters(two, one)));
    invalid(comic().withComic(chapters(one, new ComicChapter(2, "Inconnu", "b"))));
    invalid(comic().withComic(chapters(new ComicChapter(1, "Cruauté", "missing"))));
    invalid(comic().withComic(chapters(new ComicChapter(1, " ", "b"))));
    invalid(comic().withComic(chapters(new ComicChapter(0, "Cruauté", "b"))));
  }

  private static ComicDocument chapters(ComicChapter... chapters) {
    var base = comic().comic();
    return new ComicDocument(
        base.volume(),
        base.readingDirection(),
        base.annotationMethod(),
        List.of(
            newPage("a", "001.jpg", null),
            newPage("b", "002.jpg", null),
            newPage("c", "003.jpg", null)),
        List.of(chapters));
  }
}
