package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
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
  @JsonIgnore
  public List<ComicBox> boxes() {
    return pages.stream().flatMap(page -> page.boxes().stream()).toList();
  }
}
