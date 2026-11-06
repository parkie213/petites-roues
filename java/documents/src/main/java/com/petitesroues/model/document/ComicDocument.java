package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A comic body: optional volume number, reading direction, pages in manifest order, optional
 * chapters and the text boxes on the pages.
 *
 * @param volume optional positive volume (tome) number within its series; the library sorts and
 *     labels a series' volumes by it
 * @param chapters chapters in reading order, each starting on a later page than the one before;
 *     empty when the volume's chapters are unknown
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicDocument(
    Integer volume,
    ReadingDirection readingDirection,
    String annotationMethod,
    List<ComicPage> pages,
    List<ComicChapter> chapters,
    List<ComicBox> boxes) {
  public ComicDocument {
    if (readingDirection == null) readingDirection = ReadingDirection.LTR;
    pages = list(pages);
    chapters = list(chapters);
    boxes = list(boxes);
  }

  public ComicDocument withBoxes(List<ComicBox> boxes) {
    return new ComicDocument(volume, readingDirection, annotationMethod, pages, chapters, boxes);
  }

  /** Replaces the box at {@code index} with {@code change} applied to it. */
  public ComicDocument withBox(int index, UnaryOperator<ComicBox> change) {
    var changed = new ArrayList<>(boxes);
    changed.set(index, change.apply(boxes.get(index)));
    return withBoxes(changed);
  }
}
