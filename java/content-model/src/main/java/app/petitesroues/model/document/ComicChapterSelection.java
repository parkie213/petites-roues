package app.petitesroues.model.document;

import java.util.List;

/**
 * A chapter and its pages in manifest reading order. Bounds are zero-based: firstPage is inclusive,
 * endPage exclusive. Pages are immutable and retain their boxes and lines in order.
 */
public record ComicChapterSelection(
    ComicChapter chapter, int firstPage, int endPage, List<ComicPage> pages) {
  public ComicChapterSelection {
    pages = List.copyOf(pages);
  }
}
