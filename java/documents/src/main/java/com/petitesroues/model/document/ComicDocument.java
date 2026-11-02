package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A comic body: optional volume number, reading direction, pages in manifest order, optional
 * chapters and the text regions on the pages.
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
    List<ComicCue> cues) {
  public ComicDocument {
    if (readingDirection == null) readingDirection = ReadingDirection.UNSPECIFIED;
    pages = list(pages);
    chapters = list(chapters);
    cues = list(cues);
  }

  public ComicDocument withCues(List<ComicCue> cues) {
    return new ComicDocument(volume, readingDirection, annotationMethod, pages, chapters, cues);
  }

  /** Replaces the cue at {@code index} with {@code change} applied to it. */
  public ComicDocument withCue(int index, UnaryOperator<ComicCue> change) {
    var changed = new ArrayList<>(cues);
    changed.set(index, change.apply(cues.get(index)));
    return withCues(changed);
  }
}
