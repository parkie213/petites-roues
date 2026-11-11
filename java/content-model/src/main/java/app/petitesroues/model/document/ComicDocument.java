package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.list;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A comic body: optional volume number, reading direction, the reference CBZ the boxes were drawn
 * against, pages in manifest order (each holding its text boxes) and optional chapters.
 *
 * @param volume optional positive volume (tome) number within its series; the library sorts and
 *     labels a series' volumes by it
 * @param chapters chapters in reading order, each starting on a later page than the one before;
 *     empty when the volume's chapters are unknown
 */
public record ComicDocument(
    Integer volume,
    ReadingDirection readingDirection,
    ComicReference reference,
    List<ComicPage> pages,
    List<ComicChapter> chapters)
    implements StudyBody {
  public ComicDocument {
    if (readingDirection == null) readingDirection = ReadingDirection.LTR;
    pages = list(pages);
    chapters = list(chapters);
  }

  /**
   * Selects a chapter through the page before the next chapter starts, or the volume's end.
   * Introductory pages before the first chapter are excluded. Rejects unknown chapter indices,
   * missing boundary pages and non-increasing boundaries instead of returning a partial selection.
   */
  public ComicChapterSelection chapter(int index) {
    if (index < 0 || index >= chapters.size())
      throw new IllegalArgumentException("Unknown chapter index: " + index);
    ComicChapter chapter = chapters.get(index);
    int first = pageIndex(chapter.pageId());
    int end =
        index + 1 < chapters.size() ? pageIndex(chapters.get(index + 1).pageId()) : pages.size();
    if (first < 0 || end <= first)
      throw new IllegalArgumentException("Invalid chapter page boundaries");
    return new ComicChapterSelection(chapter, first, end, pages.subList(first, end));
  }

  private int pageIndex(String id) {
    for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(id)) return i;
    return -1;
  }

  public ComicDocument withPages(List<ComicPage> pages) {
    return new ComicDocument(volume, readingDirection, reference, pages, chapters);
  }

  public ComicDocument withReference(ComicReference reference) {
    return new ComicDocument(volume, readingDirection, reference, pages, chapters);
  }

  /** Replaces the page at {@code index} with {@code change} applied to it. */
  public ComicDocument withPage(int index, UnaryOperator<ComicPage> change) {
    var changed = new ArrayList<>(pages);
    changed.set(index, change.apply(pages.get(index)));
    return withPages(changed);
  }

  /** Every box in reading order: page by page, then by position on the page. */
  public List<ComicBox> boxes() {
    return pages.stream().flatMap(page -> page.boxes().stream()).toList();
  }
}
