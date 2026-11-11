package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.list;
import static app.petitesroues.model.document.Defaults.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A box on a comic page, holding the lines printed in it in reading order. Its page and reading
 * order are where it sits: the page whose {@code boxes} hold it, at that position. A speech bubble
 * or caption usually has one line; a printed text block too long for one card (a recap paragraph, a
 * character profile) is split into one line per sentence, which readers show one at a time or as a
 * list.
 *
 * <p>{@code id} identifies the box itself and never changes with its text; box operations (moving,
 * reordering, deleting) address it. Text operations address a line by the line's own ID.
 */
public record ComicBox(String id, List<ComicRegion> regions, List<Line> lines) {
  public ComicBox {
    id = text(id);
    regions = list(regions);
    lines = list(lines);
  }

  /** True if one of the box's lines has {@code lineId}. */
  public boolean holds(String lineId) {
    return lines.stream().anyMatch(line -> line.id().equals(lineId));
  }

  /** Replaces the line with {@code lineId}; the box must hold it. */
  public ComicBox withLine(String lineId, UnaryOperator<Line> change) {
    var changed = new ArrayList<>(lines);
    for (int i = 0; i < changed.size(); i++)
      if (changed.get(i).id().equals(lineId)) {
        changed.set(i, change.apply(changed.get(i)));
        return withLines(changed);
      }
    throw new IllegalArgumentException("This box holds no line " + lineId);
  }

  public ComicBox withLines(List<Line> lines) {
    return new ComicBox(id, regions, lines);
  }

  public ComicBox withRegions(List<ComicRegion> regions) {
    return new ComicBox(id, regions, lines);
  }
}
