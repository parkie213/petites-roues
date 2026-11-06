package com.petitesroues.comic;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentValidator;
import com.petitesroues.model.document.ComicChapter;
import com.petitesroues.model.document.ComicCue;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyCue;
import com.petitesroues.model.document.StudyDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComicCueValidationTest {
  private static StudyDocument comic(ComicCue... cues) {
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
        new ComicDocument(
            null,
            ReadingDirection.RTL,
            null,
            List.of(newPage("page", "001.jpg", null)),
            List.of(),
            List.of(cues)));
  }

  private static void valid(StudyDocument document) {
    assertDoesNotThrow(() -> StudyDocumentValidator.validate(document));
  }

  private static void invalid(StudyDocument document) {
    assertThrows(IllegalArgumentException.class, () -> StudyDocumentValidator.validate(document));
  }

  @Test
  void validatesPageReferencesReadingOrderAndNormalizedGeometry() {
    var cue =
        newBubble(StudyCue.of("bubble", "Salut !"), "page", 1, List.of(newRegion(.1, .2, .3, .4)));
    valid(comic(cue));
    invalid(comic(newBubble(cue.studies().getFirst(), "missing", 1, cue.regions())));
    invalid(comic(cue.withOrder(0)));
    for (var region :
        List.of(
            newRegion(-.1, 0, .1, .1),
            newRegion(.9, 0, .2, .1),
            newRegion(0, 0, 0, .1),
            newRegion(0, 0, Double.NaN, .1),
            newRegion(0, 0, .1, Double.POSITIVE_INFINITY)))
      invalid(comic(cue.withRegions(List.of(region))));

    cue = cue.withRegions(List.of(newRegion(0, 0, 1, 1)));
    var other = newBubble(StudyCue.of("other", "Oui"), "page", 1, cue.regions());
    invalid(comic(cue, other));
    other = other.withOrder(2);
    valid(comic(cue, other));
    valid(comic(cue, other.withStudy("other", s -> s.withSource(""))));
    invalid(comic(cue, other.withStudy("other", s -> s.withId("bubble"))));
  }

  @Test
  void aBoxHasItsOwnUniqueIdAndOneOrMoreUniquelyIdentifiedStudies() {
    var box = List.of(newRegion(.1, .6, .8, .3));
    var first = StudyCue.of("s1", "Une phrase.");
    var second = StudyCue.of("s2", "Une autre.");
    var block = new ComicCue("intro", "page", 1, box, List.of(first, second));
    var label = newBubble(StudyCue.of("label", "Introduction"), "page", 2, box);
    valid(comic(block, label));

    invalid(comic(block.withStudies(List.of())));
    invalid(comic(block.withStudies(List.of(first, first))));
    invalid(comic(block.withStudies(List.of(first, second.withId(" ")))));
    invalid(comic(block, newBubble(StudyCue.of("s2", "Encore"), "page", 2, box)));
    invalid(comic(new ComicCue(" ", "page", 1, box, List.of(first))));
    invalid(comic(block, new ComicCue("intro", "page", 2, box, List.of(StudyCue.of("x", "X")))));
    // Box and study IDs are separate namespaces.
    valid(comic(block, new ComicCue("s1", "page", 2, box, List.of(StudyCue.of("x", "X")))));
  }

  @Test
  void aStudyIsReplacedInPlaceByItsOwnId() {
    var block =
        new ComicCue(
            "intro",
            "page",
            1,
            List.of(newRegion(.1, .6, .8, .3)),
            List.of(StudyCue.of("s1", "Une phrase."), StudyCue.of("s2", "Une autre.")));
    var changed = block.withStudy("s2", s -> s.withTranslation("Another."));
    assertEquals("Another.", changed.studies().get(1).translation());
    assertEquals(block.studies().get(0), changed.studies().get(0));
    assertEquals("intro", changed.id());
    assertTrue(changed.holds("s2"));
    assertThrows(IllegalArgumentException.class, () -> block.withStudy("nope", s -> s));
  }

  @Test
  void volumeIsOptionalButPositive() {
    for (Integer volume : new Integer[] {null, 1, 12}) valid(comic().withComic(volume(volume)));
    for (int volume : new int[] {0, -1}) invalid(comic().withComic(volume(volume)));
  }

  private static ComicDocument volume(Integer volume) {
    var base = comic().comic();
    return new ComicDocument(
        volume,
        base.readingDirection(),
        base.annotationMethod(),
        base.pages(),
        base.chapters(),
        base.cues());
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
        List.of(chapters),
        base.cues());
  }
}
