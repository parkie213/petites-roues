package app.petitesroues.model.document;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComicChapterSelectionTest {
  private static ComicPage page(String id) {
    return new ComicPage(id, id + ".png", null, List.of());
  }

  @Test
  void selectsManifestRangesWithoutPrefaceAndIncludesFinalPage() {
    var pages = List.of(page("cover"), page("a"), page("b"), page("c"));
    var comic =
        new ComicDocument(
            null,
            null,
            null,
            pages,
            List.of(new ComicChapter(1, "First", "a"), new ComicChapter(null, "Bonus", "c")));
    var first = comic.chapter(0);
    assertEquals(1, first.firstPage());
    assertEquals(3, first.endPage());
    assertEquals(pages.subList(1, 3), first.pages());
    assertEquals(List.of(pages.get(3)), comic.chapter(1).pages());
    assertThrows(UnsupportedOperationException.class, () -> first.pages().clear());
    assertThrows(IllegalArgumentException.class, () -> comic.chapter(-1));
    assertThrows(IllegalArgumentException.class, () -> comic.chapter(2));
  }

  @Test
  void rejectsMissingOrReversedBoundaries() {
    var pages = List.of(page("a"), page("b"));
    for (var chapters :
        List.of(
            List.of(new ComicChapter(1, "Missing", "gone")),
            List.of(new ComicChapter(1, "First", "a"), new ComicChapter(2, "Missing", "gone")),
            List.of(new ComicChapter(1, "First", "b"), new ComicChapter(2, "Earlier", "a")))) {
      var comic = new ComicDocument(null, null, null, pages, chapters);
      assertThrows(IllegalArgumentException.class, () -> comic.chapter(0));
    }
  }
}
